# Multithreaded Custom Messaging Server in Java
Developed by @Multifactored

This repo contains two programs. The first (server.java) is an application that hosts the server of the chat application. The second is the client program to connect with the server with credentials. Logged in clients can communicate with each other. This was designed on, and for Debian distros running javac.

It has a range of more advanced features, such as:
* Account management on the main server, credential validation
* Broadcasting to all logged in users
* Wack custom synclocks
* User blacklist
* Account lockouts (clientside though, so it's pretty bad)

Oh yeah, and it's in UDP. Users will require frequent prayer to recieve their messages properly without packet loss.

## Usage of the Project

Compliation: `javac *.java`
Usage: 
* `java Server [port] [block_duration] [timeout]` 
* `java Client [IP/localhost] [port]`

## Contact
* Wisley Chau : multifactored@gmail.com

## Brief Documentation

Yeah, uh, it's cool. Please don't use it though.
