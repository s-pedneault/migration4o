package migration4o.database;

import com.db4o.ext.Db4oIOException;
import com.db4o.io.IoAdapter;
import com.db4o.io.MemoryIoAdapter;

/**
 * Wraps a {@link MemoryIoAdapter} to guard against invalid seek positions.
 * <p>
 * DB4O can compute negative or out-of-range slot offsets for certain corrupt
 * object references. The standard {@code MemoryIoAdapter} passes these
 * directly to {@code System.arraycopy}, which throws
 * {@code ArrayIndexOutOfBoundsException} and leaves the container in a
 * broken state (subsequent calls throw {@code DatabaseClosedException}).
 * <p>
 * This wrapper validates the seek position before every read and returns
 * zero bytes for invalid positions, allowing DB4O to treat the object as
 * unreadable without crashing the entire container.
 */
public class SafeMemoryIoAdapter extends IoAdapter {

    private final MemoryIoAdapter delegate;
    private long currentSeekPos;

    /**
     * Creates the template instance used for DB4O configuration.
     * Call {@link #put(String, byte[])} on this to load file data,
     * then pass to {@code config.io(adapter)}.
     */
    public SafeMemoryIoAdapter() {
        this.delegate = new MemoryIoAdapter();
    }

    /** Per-file instance returned by {@link #open}. */
    private SafeMemoryIoAdapter(MemoryIoAdapter delegate) {
        this.delegate = delegate;
    }

    /** Delegates to the inner adapter for file data storage. */
    public void put(String name, byte[] bytes) {
        delegate.put(name, bytes);
    }

    @Override
    public IoAdapter open(String name, boolean lockFile, long initialLength, boolean readOnly) throws Db4oIOException {
        // delegate.open() returns the per-file MemoryIoAdapter from its hashtable
        IoAdapter inner = delegate.open(name, lockFile, initialLength, readOnly);
        if (inner instanceof MemoryIoAdapter) {
            return new SafeMemoryIoAdapter((MemoryIoAdapter) inner);
        }
        return inner;
    }

    @Override
    public int read(byte[] buffer, int length) throws Db4oIOException {
        long dataLength = delegate.getLength();
        if (currentSeekPos < 0 || currentSeekPos >= dataLength) {
            System.err.println("[DB4O SafeIO] invalid read at offset " + currentSeekPos + " (dataLength=" + dataLength + ", requested=" + length + ") — returning zero bytes");
            return 0;
        }
        return delegate.read(buffer, length);
    }

    @Override
    public void seek(long pos) throws Db4oIOException {
        currentSeekPos = pos;
        if (pos < 0 || pos > delegate.getLength()) {
            // Don't call delegate.seek() with an invalid position —
            // it would set _seekPos to (int)pos which then crashes in read().
            // We log and defer the error to read() which will return 0 bytes.
            return;
        }
        delegate.seek(pos);
    }

    @Override
    public void write(byte[] buffer, int length) throws Db4oIOException {
        delegate.write(buffer, length);
    }

    @Override
    public void sync() throws Db4oIOException {
        delegate.sync();
    }

    @Override
    public long getLength() throws Db4oIOException {
        return delegate.getLength();
    }

    @Override
    public void close() throws Db4oIOException {
        delegate.close();
    }

    @Override
    public void delete(String path) {
        delegate.delete(path);
    }

    @Override
    public boolean exists(String path) {
        return delegate.exists(path);
    }
}
