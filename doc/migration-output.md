# DB4O database migration engine

Now that we have a fully-functionnal DB4O database reader and resolver, and test migration process to Excel, we need to build a complete migration engine.

We will be building an XML export engine, which receives a fully-initialized DOEngine. 

The XML engine must export several things:
1. An XML schema which will be used for the XML data files
1. A folder containing one XML file per module (just like our Excel output), with a full export of all data
1. A complete XML version of the database structure, including the number of objects migrated for each class

If we import for example 54060/premligne.dat, we should end up with:
- 54060/