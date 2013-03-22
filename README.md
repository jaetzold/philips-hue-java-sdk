An SDK for accessing a Philips Hue installation with Java.
==========================================================

The aim is a simple interface and no dependencies aside from Java 7 SE.
But since the API of the Hue is JSON based, code from the following project has been borrowed:
https://github.com/douglascrockford/JSON-java
( actual commit used: b883a848a6af94db57e1b8223ab9b6d96707a9e7 )

From what i can tell so far it works well. But some more "peripheral" methods are not really tested. For example the search for new lights.
I would need light bulbs unknown to my bridge to do that. Donations welcome ;-)
