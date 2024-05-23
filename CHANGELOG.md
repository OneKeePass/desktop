### 0.13.0
- Supports both light and dark color themes/modes now. 
- Added support for multiple languages. Though this version only has Spanish translations, the translations for other languages will be added in later releases
- Added new application level settings panel to change color theme and language preferences

### 0.12.0
- Time-based One-time Password (TOTP) can be added to an Entry
- You can use custom settings while adding a TOTP
- Generated time based token can be used for two-factor authentication (2FA) in any supported sites and apps 
- For each entry, more than one OTP fields can be added and used 

### 0.11.0
- Entry form fields are now optional except the title. An entry can be created without entering any values in the fields
- Upgraded backend tauri and frotend mui packages to the latest versions
- Fixed a bug in xml parsing module  

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
