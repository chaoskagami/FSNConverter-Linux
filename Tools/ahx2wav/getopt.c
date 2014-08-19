/*LINTLIBRARY*/
/***********************************************************************
    getopt.c  このgetopt.cはPublic Domainです
    1996.4 Hiroshi Masuda 変更
***********************************************************************/
#include <stdio.h>
#include <string.h>

int     opterr = 1;       /* getopt()エラーメッセージ 1:表示 0:非表示 */
int     optind = 1;       /* getopt()インデックス */
int     optopt;           /* 取得オプション文字 */
char    *optarg;          /* 取得オプションパラメータ文字列 */

static void errdisp(char *cmd, char *as)
{
    static char     crtail[ ] = {0, '\n', 0};

    if(opterr){
        fputs(cmd, stderr);     fputs(as, stderr);
        *crtail = optopt;   fputs(crtail, stderr);
    }
}

int getopt(int ac, char **av, char *opts)
{
    static char     *curopt = NULL;
    register char   *cp;

    if(curopt == NULL || !*curopt){
        curopt = av[optind];
        if(optind >= ac || *curopt != '-' || !curopt[1])
            return(EOF);                 /* オプション指定なし */
        if(!strcmp("-", ++curopt)){
            ++optind;
            return(EOF);                 /* -- 指定 */
        }
    }
    optopt = *curopt++;                 /* option文字格納 */
    cp = strchr(opts, optopt);          /* option文字検索 */
    if(optopt == ':' || cp == NULL){    /* option文字不正 or 検索失敗 */
        errdisp(*av, ": unknown option, -");
        if(!*curopt)
            ++optind;
        return('?');
    }
    if(*++cp == ':'){                   /* optionパラメータあり */
        ++optind;
        if(*curopt){
            optarg = curopt;
            curopt = NULL;
        }else{
            if(optind >= ac){
                errdisp(*av, ": argument missing for -");
                return('?');
            }else{
                optarg = av[optind++];
            }
             /* now *curopt == '\0' */
        }
    }else{
        optarg = NULL;
        if(!*curopt) ++optind;
    }
    return(optopt);
}
