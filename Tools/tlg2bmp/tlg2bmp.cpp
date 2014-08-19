#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

typedef int32_t LONG;
typedef uint32_t DWORD;
typedef uint8_t BYTE;
typedef uint16_t WORD;

typedef struct tagBITMAPINFOHEADER {
  DWORD biSize; //4
  LONG  biWidth; //8
  LONG  biHeight; //12
  WORD  biPlanes; //14
  WORD  biBitCount; //16
  DWORD biCompression; //20
  DWORD biSizeImage; //24
  LONG  biXPelsPerMeter; //28
  LONG  biYPelsPerMeter; //32
  DWORD biClrUsed; //36
  DWORD biClrImportant; //40
} __attribute__((packed)) BITMAPINFOHEADER, *PBITMAPINFOHEADER;

typedef struct tagBITMAPFILEHEADER {
  WORD  bfType;
  DWORD bfSize;
  WORD  bfReserved1;
  WORD  bfReserved2;
  DWORD bfOffBits;
} __attribute__((packed)) BITMAPFILEHEADER, *PBITMAPFILEHEADER;

typedef unsigned char uchar;

typedef struct {
	char signature[11];
	uchar colors;
	LONG width;
	LONG height;
	LONG blockheight;
} __attribute__((packed)) TLGHeader;

typedef struct {
	uchar flag;
	LONG size;
} __attribute__((packed)) BlockHeader;

void Convert(FILE* input, FILE* output);

int main(int argc, char** argv)
{
	if (argc < 2){
		printf("%s inputfile outputfile\n", argv[0]);
		return 0;
	}
	char* inputfile = argv[1];
	char* outputfile = argv[2];

	FILE* f = fopen(inputfile, "rb");
	FILE* o = fopen(outputfile, "wb");
	if (f == NULL || o == NULL){
		fprintf(stderr, "fopen error\n");
		return 1;
	}

	Convert(f, o);
	fclose(f);
	fclose(o);
	return 0;
}


int DecodeLZSS(uchar* out, uchar* in, int insize, uchar* text, int initialr)
{
	int r = initialr;
	unsigned int flags = 0;
	const uchar* inlim = in + insize;
	while(in < inlim)
	{
		if(((flags >>= 1) & 256) == 0)
		{
			flags = *in++ | 0xff00;
		}
		if(flags & 1)
		{
			int mpos = in[0] | ((in[1] & 0xf) << 8);
			int mlen = (in[1] & 0xf0) >> 4;
			in += 2;
			mlen += 3;
			if(mlen == 18) mlen += 0[in++];

			while(mlen--)
			{
				*out++ = text[r++] = text[mpos++];
				mpos &= (4096 - 1);
				r &= (4096 - 1);
			}
		}
		else
		{
			unsigned char c = 0[in++];
			*out++ = c;
			text[r++] = c;
/*			0[out++] = text[r++] = 0[in++];*/
			r &= (4096 - 1);
		}
	}
	return r;
}



void Convert(FILE* input, FILE* output)
{
	TLGHeader header;
	fread(&header, 1, sizeof(header), input);

	if(0 != memcmp("TLG5.0\x00raw\x1a", header.signature, sizeof(header.signature))){
		fprintf(stderr, "signature error");
		exit(1);
	}
	if (header.colors != 3 && header.colors != 4){
		fprintf(stderr, "unsupported colors");
		exit(1);
	}

	// write bitmap header
	BITMAPFILEHEADER bf;
	BITMAPINFOHEADER bi;
	memset(&bf, 0, sizeof(bf));
	memset(&bi, 0, sizeof(bi));

	bf.bfType = 'B' | ('M' << 8);
	bf.bfOffBits = sizeof(bf) + sizeof(bi);

	bi.biSize = sizeof(bi);
	bi.biWidth = header.width;
	bi.biHeight = header.height;
	bi.biPlanes = 1;
	bi.biBitCount = header.colors * 8;

	fwrite(&bf, 1, sizeof(bf), output);
	fwrite(&bi, 1, sizeof(bi), output);
	int imagestart = ftell(output);

	// skip tlg block size sectionblock
	long blockcount = ((header.height - 1) / header.blockheight) + 1;
	fseek(input, 4*blockcount, SEEK_CUR);

	uchar* outbuf[4];
	uchar* text = (uchar*)malloc(sizeof(uchar) * 4096);
	memset(text, 0, 4096);
	uchar* inbuf = (uchar*)malloc(sizeof(uchar) * header.blockheight * header.width + 10);
	for (int i=0; i < header.colors; i++){
		outbuf[i] = (uchar*)malloc(sizeof(uchar) * header.blockheight * header.width + 10);
	}

	int r = 0;

	uchar *prevline = (uchar*)malloc(sizeof(uchar) * header.width * header.colors);
	memset(prevline, 0, header.width * header.colors);
	for(int y_blk = 0; y_blk < header.height; y_blk += header.blockheight)
	{
		// read file and decompress
		int i;
		for(i = 0; i < header.colors; i++)
		{
			BlockHeader h;

			fread(&h, 1, sizeof(h), input);
			if(h.flag == 0)
			{
				// modified LZSS compressed data
				fread(inbuf, 1, h.size, input);
				r = DecodeLZSS(outbuf[i], inbuf, h.size, text, r);
			}
			else
			{
				// raw data
				fread(outbuf[i], 1, h.size, input);
			}
		}

		// compose colors and store
		int y_lim = y_blk + header.blockheight;
		if(y_lim > header.height)
			y_lim = header.height;

		uchar* outbufp[4];
		for(i = 0; i < header.colors; i++)
			outbufp[i] = outbuf[i];
		for(int y = y_blk; y < y_lim; y++)
		{
			uchar* prevp = prevline;
			uchar pr = 0, pg = 0, pb = 0, pa = 0;
			int x = 0;
			fseek(output, imagestart + header.colors*header.width*(header.height - (y+1)), SEEK_SET);
			switch(header.colors)
			{
			case 3:
				for(pr = 0, pg = 0, pb = 0, x = 0;
					x < header.width; x++)
				{
					int b = outbufp[0][x];
					int g = outbufp[1][x];
					int r = outbufp[2][x];
					b += g; r += g;
					pb += b;
					pg += g;
					pr += r;

					*prevp += pb; fputc(*prevp, output); prevp++;
					*prevp += pg; fputc(*prevp, output); prevp++;
					*prevp += pr; fputc(*prevp, output); prevp++;
				}
				outbufp[0] += header.width;
				outbufp[1] += header.width;
				outbufp[2] += header.width;
				break;

			case 4:
				for(pr = 0, pg = 0, pb = 0, pa = 0, x = 0;
					x < header.width; x++)
				{
					int b = outbufp[0][x];
					int g = outbufp[1][x];
					int r = outbufp[2][x];
					int a = outbufp[3][x];
					b += g; r += g;
					pb += b;
					pg += g;
					pr += r;
					pa += a;
					*prevp += pb; fputc(*prevp, output); prevp++;
					*prevp += pg; fputc(*prevp, output); prevp++;
					*prevp += pr; fputc(*prevp, output); prevp++;
					*prevp += pa; fputc(*prevp, output); prevp++;
				}
				outbufp[0] += header.width;
				outbufp[1] += header.width;
				outbufp[2] += header.width;
				outbufp[3] += header.width;
				break;
			}
		}
	}
}
