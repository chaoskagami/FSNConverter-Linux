What is this?

- Yet another one of my random portish things. The Fate/Stay night VNDS 
converter at first glance looked very unixy-okay, so I figured I might 
be able to just run it on linux.
- Not exactly.

Here was my list of concerns:
 - Bundled tools.
 - Java code putting C: and Z: in automatically.
 - No regard for system tools.
 - Source code is windows specific.
 - Launch4j needs butchering.

Basically, I've fixed all of these imperfectly. Enough to use it to do 
its job. As proof, the fate version I'm playing right now was a byproduct of
this.

 - Misc tools converted. Lotta windows calls ugh
 - Java code altered a bit. Some things needed to go.
 - tools has been renamed to bin, and we append it to the path instead.
 - System tools preferred, and in most cases, required.

Install these deps or you won't be getting anywhere:
 - pngcrush
 - ffmpeg/libav
 - liboggz
 - ant
 - java1.6
These are optional.
 - pngquant
 - pngnq

FAQ:
	Q: So let me get this straight. Fate is a windows game, and you're 
	   running a tool on linux to convert for android. WTF is wrong with
	   you.
	A: There's this thing called a 'canadian-cross' compiler. This is like
	   that. So no, not crazy.

	Q: Does this work?
	A: For the most part. During conversion I DID get a few warnings spat
	   out about some tlg files.

	Q: I never managed to get fate to run on wine. HELP
	A: Install Dx9. Also, you probably won't get the videos working so
	   rename video.xp3 to video.xp3.disabled or something.

For license read COPYING. tl;dr same as weaboo.nl, since I can't change the
license ( GPLv2 ) nor do I want to in this case.
