#LikeBelt#
A fun project from [Deeplocal](http://deeplocal.com) : Android app + NFC + Facebook

[INFO](http://likebelt.com)

Reads NFC tags (only tested with Mifare 1K) and performs Facebook operations based on tag contents. 

##Physical##
For the belt itself I used a second antennta and extra phone back plate that I extended the leads on. This lets me put the nfc antenna on the buckle of the belt. The tags used are Mifare 1K that I just coded with NDEF data with following formats.

##Tag Format##
profile:abcdefg, it will open facebook to that profile

place:zyxw, it will check in to that place on facebook

like:zzzz, it will look for a url field on the tag, if it exists, it will prompt the user to like it

wall:aaaa, it will post aaaa to their facebook wall

##Notes##
Code uses some parts of NFCDemo sample + info from this [great post] (http://mifareclassicdetectiononandroid.blogspot.com/2011/04/reading-mifare-classic-1k-from-android.html). 

This code was hacked together quickly and is just used for fun around the office. Its by no means production ready, but hopefully its a good starting point / example.

Drop [Deeplocal](http://deeplocal.com) a line.