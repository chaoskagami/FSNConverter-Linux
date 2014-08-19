#!/bin/bash

F=$1

if [ "$F" == "build" ]; then
	rm -rf Out

	cd ./UI && ant && cd ../

	cp -ra Distrib Out

	cp UI/FSN* Out/
	cp -ra UI/lib Out/lib
	cp -ra UI/template Out/template

	mkdir tmp && cd tmp
	wget http://unclemion.com/dev/attachments/download/43/onscrtools-linux-x86_64-20110930.tar.bz2
	tar -xvpf onscrtools-linux-x86_64-20110930.tar.bz2
	cd ..

	cp -a tmp/onscrtools-linux-x86_64-20110930/bin/* Out/bin/

	rm -rf tmp

	cd Tools/

	cd ahx2wav
	chmod +x make.sh
	./make.sh ahx2wav
	mv ahx2wav ../../Out/bin/

	cd ../ima2raw
	g++ -o ima2raw ima2raw.cpp
	mv ima2raw ../../Out/bin/

	cd ../tlg2bmp
	g++ -o tlg2bmp tlg2bmp.cpp
	mv tlg2bmp ../../Out/bin/	
elif [ "$F" == "clean" ]; then
	rm -rf Out
	cd Tools/ahx2wav
	./make.sh clean
	cd ../../UI
	ant clean
	cd ..
fi
