Fate/Stay Night VNDS Converter (on linux!) [![Build Status](https://travis-ci.org/chaoskagami/FSNConverter-Linux.svg?branch=master)](https://travis-ci.org/chaoskagami/FSNConverter-Linux)

Yet another one of my random portish things. The Fate/Stay night VNDS converter from over at weaboo.nl - at first glance looked very unixy-okay, so I figured I might be able to just run it on linux.
It wasn't nearly that simple. Some code used windows APIs for files, and some code was weird.

The issues I ran into:
 - Posix API not used for file access. I replaced the API in C close to 1:1
 - Bad code. Like 0[ptr++] . It's not ASLR friendly. Hell, it should probably just segfault.
 - Do not hardcode C: and Z: into your java. Repeat, don't hardcode windows paths.
 - ffmpeg and libav are both the same thing, but the program only uses ffmpeg
 - System tools really should be preferred and/or required on linux.

These are needed to compile this, as well as the below
 - ant
 - gcc
 - g++
 - bash (I don't use ash or zsh. Sorry. Some adjustment might be needed.)
 - > jdk1.6

Install these dependencies to run the blob
 - pngcrush
 - ffmpeg/libav
 - liboggz
 - > java1.7 (Note: I build with openjdk-7. There's no reason why 1.6 shouldn't work other than outdatedness)

These are optional as long as you don't use the quantization functions
 - pngquant
 - pngnq

Q  So let me get this straight. Fate is a windows game, and you're 
   running a tool on linux to convert for android. WTF is wrong with
   you.
A  There's this thing called a 'canadian-cross' compiler. This is like
   that. So no, not crazy.

Q  Does this work?
A  For the most part. During conversion I DID get a few warnings spat
   out about some tlg files.

Q  I never managed to get fate to run on wine. HELP
A  Install Dx9. Also, you probably won't get the videos working so
   rename video.xp3 to video.xp3.disabled or something.


For license read COPYING. tl;dr same as weaboo.nl, since I can't change the
license ( GPLv2 ) nor do I want to in this case.
