### 0.10.0
- Entry listing can now be sorted based on Title or Modified time or Created time
- Added Tags based entry category for listing entries
- Added menu action to empty the Recycle bin
- Can now save to a backup file using the app level menu - 'Save Database Backup' 

### 0.9.0
- Entry attachments are now supported. You can attach any number of files to an entry,view and store securely
- Password is now optional. Accordingly you can use only a master password or only a key file or both to secure your database
- [Botan cryptography library](https://botan.randombit.net/) is now used for the database encryption/decryption. This improved the database read/write performance significantly

### 0.8.0
- Basic Auto-Type feature that sends simulated keypresses to other applications in macOS and soon to be added for other platforms.See [Auto-Type](./docs/AUTO-TYPE.md) doc for additional details
#### Fixed
-  Groups menu is empty #5  - Thanks [gregordinary](https://github.com/gregordinary)
-  Password Generator Does Not Save Generated Value to Entry #6 - Thanks [gregordinary](https://github.com/gregordinary)
-  Password strength indicator does not update on password edit field. #7 Thanks [gregordinary](https://github.com/gregordinary)
-  Error when attempting to clear password length in the password generator #8 Thanks [gregordinary](https://github.com/gregordinary)
-  Text boxes lack left and right padding #9   - Thanks [gregordinary](https://github.com/gregordinary)


### 0.7.0
- Quick database unlock feature using TouchID for Mac OS added 
- Removed storing the original credentials in memory and added secured way of storing the keys in memory for quick access
- Additional protection using key file added. In addition to using any file as key file, the key file (an XML file) can also be generated  
- Added proper error messages when user attempts to open a database with old format
- Fixed issues with Save as action in Windows platform

### 0.6.0
- Before saving any changes made in the current database, the database file is checked whether it was changed externally. If any changes detected, the user is presented with options to take an appropriate action

### 0.5.0

- Supports the KeePass-compatible database (Kdbx 4.x)
- Entries are grouped as Types or Categories or the standard Group tree
- Custom fields can be organized as sections
- Any number of databases can be created and used
- You can create your own custom entry type with any set of fields can be created as template and used to create entries
