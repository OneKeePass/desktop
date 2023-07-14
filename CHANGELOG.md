### 0.7.0
- Quick database unlock feature using TouchID for Mac OS added 
- Removed storing the original credentials in memory and added secured way of storing the keys in memory for quick access
- Additional protection using key file added. In addition to using any file as key file, the key file (an XML file) can also be generated  
- Added proper error messaages when user attempts to open a database with old format
- Fixed issues with Save as action in Windows platform

### 0.6.0
- Before saving any changes made in the current database, the database file is checked whether it was changed externally. If any changes detected, the user is presented with options to take an appropriate action

### 0.5.0

- Supports the KeePass-compatible database (Kdbx 4.x)
- Entries are grouped as Types or Categories or the standard Group tree
- Custom fields can be organized as sections
- Any number of databases can be created and used
- You can create your own custom entry type with any set of fields can be created as template and used to create entries
