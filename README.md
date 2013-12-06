POP3 Mail Server
================

A minimal implementation of a POP3 Mail Server, developed for the G52APR module at the University of Nottingham. The mail server implements the following POP3 commands:
 * USER
 * PASS
 * QUIT
 * STAT
 * LIST
 * NOOP
 * UIDL
 * DELE
 * RETR
 * TOP

Using the server
----------------
The server can be executed from the commandline as follows:

    java Pop3Server port [timeout]
        
where `port` is the port number for the server to listen on and `timeout` is an optional parameter to specify the timeout of individual connections in seconds.


Database configuration
----------------------
Certain database tables are required for the POP3 Server to function correctly. An SQL file is provided, `\sql\mkdb.sql`, which creates these tables and fills them with sample emails for testing purposes.

In addition, the file `\src\Database.java` requires correct database connection information.
