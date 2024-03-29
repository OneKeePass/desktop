# Frequently Asked Questions

Here are some common ones. More will be added in due time

## Where and how the database is stored?
OneKeePass stores all your passwords and other details in a single encrypted file in any place of your choosing in the file system 

## What is the format of the OneKeePass database?
OneKeePass supports the well known [KeePass](https://keepass.info/help/kb/kdbx_4.1.html) database format KDBX 4


## How many databases can be opened ?
You can open many databases at the same time. Each database is opened in a separate tab

## How to synchrozie the database file between devices?
OneKeePass does not do any automatic synchronization at this time. As the password database is a single file, you can 
use any of the cloud storage service for the synchronization between devices and also for the backup

## What is a key file ?
A key file is a file containing random bytes that is used in addition to your master key for additional security. You can basically use any file you want as a key file. Such a file should have random bytes data and the content of this random data remains the same as long as it is used as key file.

## What is mater key?
The database file is encrypted using a master key. This master key is derived using multiple components: a master password, a key file or both

Accordingly you can use only a master password or only a key file or both to secure your database

## How are entries organized ?
Entries are organized so that you can view them as  Entry types or Categories or Group tree. 

<details>
<summary>Types</summary>
<h1 align="center">
  <img src="../screenshots/Entry-Cat-Types.jpg" alt=""  />
  <br>
</h1>
</details>

<details>
<summary>Categories</summary>
<h1 align="center">
  <img src="../screenshots/Entry-Cat-Categories.jpg" alt=""  />
  <br>
</h1>
</details>

<details>
<summary>Groups</summary>
<h1 align="center">
  <img src="../screenshots/Entry-Cat-Groups.jpg" alt=""  />
  <br>
</h1>
</details>


## What are the entry categories ?
It is just the flattened list of keepass groups instead of a tree/folder like structure


## What is an entry type?
Each entry type has certain set of fields. For example *Login* entry type include fields like username, password, url etc.
OneKeePass supports some built-in standard entry types: Login, Credit/Debit Card, Bank Account and Wireless Router.
More standard entry types will be added. 


## Can I create custom entry type?
You can create a custom type with sections and fields. Such custom entry type can be used as template while creating new entries

<details>
<summary>See the image here</summary>
<h1 align="center">
  <img src="../screenshots/New-Custom-Entry-Type.jpg" alt=""  />
  <br>
</h1>

</details>

## Are file attachments supported?
Yes. You can attach any number of files to an entry. In the entry form screen, you can upload, view and delete. Any previously attahed file can be copied to a location outside the database.

It is recommended to use this feature only to store few/small files.
 
As these attached file contents are encrypted and stored within the database, attaching many/large files is considered to be out of the scope of a password manager. The database opening and saving then will be slow. It is better to use a specialized file encryption softwares - VeraCrypt,Cryptomator - to store many/large files

## Is Auto-Type supported ?
Yes. For now few basic features are supported for macOS and soon supports for other platforms will be added. See [here](./AUTO-TYPE.md) for additional details







