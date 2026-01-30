// package migration4o;

// import migration4o.engine.DOEngine;
// import migration4o.engine.migration.XMLMigrationEngine;

// public class Migration4o {
// public static void main(String[] args) {
// System.out.println("Migration4o - Database Export Tool");
// try {
// // Create engine with schema and database
// // DOEngine engine = new DOEngine("schema/migration-schema.xml",
// // "local/00000/PremLigne.dat");
// DOEngine engine = new DOEngine("schema/migration-schema.xml",
// "local/54060/BackupManuel.dat");

// // Export database to XML files with schema and report
// System.out.println("\n=== EXPORTING TO XML ===");
// XMLMigrationEngine xmlEngine = new XMLMigrationEngine();
// xmlEngine.exportToXML(engine);
// System.out.println("XML migration export completed successfully!");

// } catch (Exception e) {
// e.printStackTrace();
// }
// }

// }
