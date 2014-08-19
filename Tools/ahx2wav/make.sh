#!/bin/bash

F=$1

if [ "$F" == "ahx2wav" ]; then
	gcc -c ahx2wav.c
	gcc -c getopt.c
	gcc -lm getopt.o ahx2wav.o -o ahx2wav
elif [ "$F" == "clean" ]; then
	rm getopt.o ahx2wav.o ahx2wav
fi
