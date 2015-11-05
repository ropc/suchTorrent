TODO
====

Phase 2
-------

- upload
- time download
- contact tracker with STOPPED when client exits

Messages
--------

- Make messages have two pointers in order to avoid copying whole piece arrays. Then make it so that when writing to a socket, the two arrays are sent in sequence. *(interfacers are nice)*
	1. For header array
	2. For payload array
