### 0.25.0

#### Changes
- Memory security on lock — when a database is locked, its decrypted content is removed from memory and kept only as ciphertext until the database is unlocked again; entry attachment contents are protected the same way and the plain content is zeroized
- Databases are now locked automatically when the computer goes to sleep or suspends, on macOS, Windows and Linux — the content is encrypted before the memory image can reach a hibernation/sleep file, and the lock screen is shown on resume
- Quick unlock is now available on Windows using **Windows Hello** (PIN or biometric), alongside TouchID/FaceID on macOS
- The encryption key held for an open database is now kept in the platform's own secure store on every platform — Windows Credential Manager on Windows and the freedesktop Secret Service on Linux, matching the existing macOS Keychain use
- A locked database is no longer reachable by the browser extension — password autofill, passkey registration and passkey authentication only see unlocked databases
- Saving now considers only unlocked and modified databases. The messages shown when closing a database or quitting the application explain what will and will not be saved when some of the open databases are locked
- Auto save after an edit — an application setting (off by default) that saves the database as soon as an edit is completed [#90](https://github.com/OneKeePass/desktop/issues/90)
    - Applies to entry, group, custom entry type and database settings edits
    - Imports, merges, deletions and moves are deliberately not auto saved — those wait for you to review and save
- **Backup copies to keep** setting added — how many backup files are retained for each database when a backup directory is configured
- CSV import now recognises the export files of many well known password managers — Bitwarden, 1Password, LastPass, NordPass, Proton Pass, Dashlane, Safari/iCloud Passwords, Firefox and Chrome/Edge
    - The format is detected from the file's header row and the column mapping is filled in for you to check and correct before anything is imported
    - An unrecognised export can still be mapped by hand as a generic CSV
    - Card and identity data, from the exports that carry it, is imported into the matching OneKeePass entry types instead of being flattened into notes
- Password generator improvements
    - Generated passwords are shown in a monospaced font with the characters colour coded by type, both in the generator and in the entry form
    - **Exclude similar characters** option added
    - The options you choose are now remembered across sessions
- The database settings **Security** tab is now called **Encryption**, and each of its fields — cipher, key derivation function, memory, parallelism and iterations — has a help tooltip explaining what it does and what changing it costs
- System menu actions added for **Clone Entry**, **Delete Entry**, **Clone Group** and **Delete Group**, with keyboard shortcuts; the entry and group menus are enabled only when the corresponding action is possible
- **Merge Database** is enabled only when a database is open and unlocked
- Auto-capitalize, auto-correct and spell-check are turned off on text fields
- User interface refinements — flat tabs and buttons (no click ripple, labels keep their natural casing), aligned entry list and entry form footers, adjusted toolbar icon spacing, themed entry count pills and consistent spacing for the icons inside text fields

#### Fixed
- Copy and paste of a password generated from **Tools -> Password Generator** now works on Windows and Linux [#87](https://github.com/OneKeePass/desktop/issues/87)
- Sidebar scrollbar no longer appears in the middle of the entry list panel [#88](https://github.com/OneKeePass/desktop/issues/88)
- Cursor focus is placed in the **Search term** field when the search popup opens [#89](https://github.com/OneKeePass/desktop/issues/89)
- System menu keyboard shortcuts now work on Windows
- The browser extension proxy no longer consumes a full CPU core
- A database whose XML has no declaration line can now be opened [#70](https://github.com/OneKeePass/desktop/issues/70)

### 0.24.0

#### Changes
- Windows Portable version — a standalone zip that runs without installation and keeps all application data (preferences, logs, backups, word lists) in an `onekeepass-data` folder next to the exe; suitable for USB drives [#51](https://github.com/OneKeePass/desktop/issues/51)
    - Requires the Microsoft Edge WebView2 Evergreen Runtime (preinstalled on current Windows 10/11)
    - The browser extension helper's log also stays inside the portable folder
- Keyboard shortcuts added for **Copy Username** and **Copy Password** [#79](https://github.com/OneKeePass/desktop/issues/79)
- All standard field copy actions are now also available in the system menus
- The entry form now shows the entry type as a title text; **SSH Key** entries include a **UserName** field
- Leftover temporary attachment files from a crashed session are now cleaned up at app startup in addition to app quit

#### Fixed
- Non-English input methods (IME) such as Chinese and Japanese now work in all text fields on all platforms [#85](https://github.com/OneKeePass/desktop/issues/85), [#65](https://github.com/OneKeePass/desktop/issues/65)
- Session timeout setting is now applied fully [#80](https://github.com/OneKeePass/desktop/issues/80)
- Cursor focus is placed in the Password field when the Unlock Database dialog opens [#81](https://github.com/OneKeePass/desktop/issues/81)
- Linux AppImage no longer shows a blank window on distros with a newer graphics stack than the build host (Fedora, Arch, ...) — the bundled Wayland libraries that conflicted with the host's Mesa/EGL are no longer included [#58](https://github.com/OneKeePass/desktop/issues/58)
- Blank app window on Linux systems where WebKitGTK's DMA-BUF renderer fails (NVIDIA proprietary drivers, virtual machines) is fixed — the renderer is now disabled at startup

### 0.23.0

#### Changes
- New standard entry types added — **SSH Key**, **Identity**, **Passport** and **Driver License**
- SSH Agent service — store SSH keys as **SSH Key** entries and use them with an SSH agent. Two modes are supported:
    - **Agent Mode** — OneKeePass runs its own SSH agent and exposes a socket (macOS/Linux) or named pipe
    - **Client Mode** — OneKeePass adds keys to your existing system SSH agent; on Windows both **OpenSSH** (agent pipe) and **Pageant** transports are supported
    - Per-key **Require Confirmation** prompts before each signing request, and a configurable agent key lifetime
    - Keys are removed automatically when databases are locked or the service is stopped
- SSH key handling — parse keys from attachments, support PuTTY `.ppk` keys, and use a **Private Key Passphrase** label in the SSH key section
- Custom fields can now be **Text**, **Boolean** or **Date** type instead of plain text only; a desktop date picker is used for date entry and editing
- User interface refresh — group/entry/form panels now use fixed proportional widths instead of a split pane; rounded selection highlights, themed colors for the toolbar and lists, colored entry list avatars, and entry-count pills for categories and group tree items
- Additional edit options added to the system menu
- Custom icons are now shown in the search result entry list avatars

#### Fixed
- Entry list title sorting is now case-insensitive [#77](https://github.com/OneKeePass/desktop/issues/77)
- Stale data after registering a Passkey resolved by refreshing the database consistently
- Entry category highlighting and entry list loading kept consistent when a new entry is added
- Copying and clearing protected field values on timeout now works on Linux

### 0.22.0

#### Changes
- Custom icons support — upload icons from a local file or auto-fetch a site's favicon by URL; assign custom icons to any entry or group; manage all custom icons stored in the database from the new **Manage Icons** dialog
- Direct SFTP and WebDAV remote database support — create new databases or open existing ones stored on a remote SFTP or WebDAV server without any intermediate sync tool
- New entry types **SFTP Connection** and **WebDAV Connection** — store remote server credentials securely inside the database; these entries are used automatically when opening or saving a remote database
- Check for Updates — the app now checks for new releases at startup and a manual check is available from the Help menu
- Brave browser extension support added alongside existing Firefox and Chrome support
- Application log files now use rotation instead of a new timestamped file per session

### 0.21.0
- This is Mac store release of previous version 0.20.0

### 0.20.0

#### Changes
- Recently opened databases list added — quickly reopen previously used databases from the menu; old entries can be removed from the list
- Tabs can now be rearranged by drag and drop
- Entries can be selected and moved to a different group using drag and drop
- Right-click context menus added for entry list items — separate menus for single and multi-selection
- An entry can now be cloned to another open database (cross-database cloning)
- Merge Opened Databases option added — allows merging two databases that are currently open
- External database change detection and auto-merge: if the database file is changed externally by another instance or sync tool, the changes are detected and merged automatically
- File Management panel added — configure backup file flag and the directory where backups are stored
- Clipboard timeout now also applies when copying protected fields via the system menu
- Moving a group or entry to a copy of the same database is now prevented; the user is advised to use Merge Database instead
- Destination database list in Move Entry dialog now marks the current database for clarity
- Tab tooltip shows the full database file path
- Backup file naming normalised and adjusted
- Additional translations of texts
    - Italian — Thanks to contributors
    - Russian — Thanks [yudindm](https://github.com/yudindm)

#### Fixed
- Tree View duplicate UUIDs causing crashes resolved
- Split-pane auto-expansion beyond `maxSize` fixed
- Group tree view stability improved by adding React error boundaries
- Group tree mount/unmount issue on group move action fixed
- Group form refactored to include UUID and corrected display when showing read-only details
- Password content visibility in multiline fields fixed

### 0.19.0
- Added Passkey support and browser integration/proxy modules updated accordingly
- With the latest OneKeePass-Browser extension, user should be able to create a passkey for any site that allows passkey creation and similarly can do passkey authentication


#### Fixed
- non-English input method bug [#65](https://github.com/OneKeePass/desktop/issues/65) - Thanks [PandaFang](https://github.com/PandaFang)
- Deleted groups are excluded from showing up in certain UI pages

### 0.18.0

#### Changes
- Browser extension backend support enhancement
- Additional translations of texts 
    - Finnish  - Thanks [JTi65](https://github.com/JTi65)

#### Fixed
- Firefox browser extension not connecting [#61](https://github.com/OneKeePass/desktop/issues/61) - Thanks [alinmesser](https://github.com/alinmesser)
- System menus now uses the language specific translations which failed to do so in 0.17.0

### 0.17.0

#### Changes
- Browser extension backend support added for Firefox and Chrome browser extensions
- Multi line fields display/editing [#48](https://github.com/OneKeePass/desktop/issues/48) - Thanks [WolfganP](https://github.com/WolfganP)
- Additional translations of texts 
    - Arabic  - Thanks [AhmedGamal](https://github.com/AhmedGamal)

#### Fixed
- Auto Type not working [#52](https://github.com/OneKeePass/desktop/issues/52) - Thanks [starwars1999](https://github.com/starwars1999)

### 0.16.0
#### Changes
- Merging of databases can now be done
- Any previously exported passwords as CSV (comma separted values) file can be imported 
- Both Argon2d and Argon2id key derivation function (KDF) variants are now supported
- A group or an entry can be moved from one parent group to another

### 0.15.0
#### Changes
- Automatic Database Opening feature to open one or more databases automatically when you open a single database. For this, entries are to be created using the entry template **Auto Database Open** in a special group **AutoOpen**. See FAQ how to use this feature

- Added Diceware Passphrase generator with multiple words list support 

- Using standard place holder variables in entry field values are now supported

- Cloning an entry now supports replacing the username and password with references

- Additional translations of texts 

    - German  - Thanks [imar-io](https://github.com/imar-io)

    - Chinese - Thanks [CSLukkun](https://github.com/CSLukkun)

- Upgraded backend tauri and frontend mui packages 

### 0.14.0
#### Changes
- Added cloning an entry
- Groups can be sorted based on its name
- Upgraded backend tauri and frontend mui packages to the latest versions
#### Fixed
-  Case insensitive search #29 - - Thanks [adrian-e](https://github.com/adrian-e)

### 0.13.0
#### Changes
- Supports both light and dark color themes/modes now. 
- Added support for multiple languages. Though this version only has Spanish translations, the translations for other languages will be added in later releases
- Added new application level settings panel to change color theme and language preferences

### 0.12.0
#### Changes
- Time-based One-time Password (TOTP) can be added to an Entry
- You can use custom settings while adding a TOTP
- Generated time based token can be used for two-factor authentication (2FA) in any supported sites and apps 
- For each entry, more than one OTP fields can be added and used 

### 0.11.0
#### Changes
- Entry form fields are now optional except the title. An entry can be created without entering any values in the fields
- Upgraded backend tauri and frontend mui packages to the latest versions
- Fixed a bug in xml parsing module  

### 0.10.0
#### Changes
- Entry listing can now be sorted based on Title or Modified time or Created time
- Added Tags based entry category for listing entries
- Added menu action to empty the Recycle bin
- Can now save to a backup file using the app level menu - 'Save Database Backup' 

### 0.9.0
#### Changes
- Entry attachments are now supported. You can attach any number of files to an entry,view and store securely
- Password is now optional. Accordingly you can use only a master password or only a key file or both to secure your database
- [Botan cryptography library](https://botan.randombit.net/) is now used for the database encryption/decryption. This improved the database read/write performance significantly

### 0.8.0
#### Changes
- Basic Auto-Type feature that sends simulated keypresses to other applications in macOS and soon to be added for other platforms.See [Auto-Type](./docs/AUTO-TYPE.md) doc for additional details
#### Fixed
-  Groups menu is empty #5  - Thanks [gregordinary](https://github.com/gregordinary)
-  Password Generator Does Not Save Generated Value to Entry #6 - Thanks [gregordinary](https://github.com/gregordinary)
-  Password strength indicator does not update on password edit field. #7 Thanks [gregordinary](https://github.com/gregordinary)
-  Error when attempting to clear password length in the password generator #8 Thanks [gregordinary](https://github.com/gregordinary)
-  Text boxes lack left and right padding #9   - Thanks [gregordinary](https://github.com/gregordinary)


### 0.7.0
#### Changes
- Quick database unlock feature using TouchID for Mac OS added 
- Removed storing the original credentials in memory and added secured way of storing the keys in memory for quick access
- Additional protection using key file added. In addition to using any file as key file, the key file (an XML file) can also be generated  
- Added proper error messages when user attempts to open a database with old format
- Fixed issues with Save as action in Windows platform

### 0.6.0
#### Changes
- Before saving any changes made in the current database, the database file is checked whether it was changed externally. If any changes detected, the user is presented with options to take an appropriate action

### 0.5.0
#### Changes

- Supports the KeePass-compatible database (Kdbx 4.x)
- Entries are grouped as Types or Categories or the standard Group tree
- Custom fields can be organized as sections
- Any number of databases can be created and used
- You can create your own custom entry type with any set of fields can be created as template and used to create entries
