#include "dnet_crypt.h"
#include "dfslib_string.h"
#include "dfslib_crypt.h"
#include "dfsrsa.h"
#include "crc.h"
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#define MINERS_PWD             "minersgonnamine"
#define SECTOR0_BASE           0x1947f3acu
#define SECTOR0_OFFSET         0x82e9d1b5u
static struct dfslib_crypt *g_crypt = 0;
#define DNET_KEYLEN	((DNET_KEY_SIZE * 2) / (sizeof(dfsrsa_t) * 8))



int crypt_init_test(void)
{
    struct dfslib_string str;
    uint32_t sector0[128];
    int i;

    g_crypt = (struct dfslib_crypt*)malloc(sizeof(struct dfslib_crypt));
    if(!g_crypt) return -1;
    dfslib_crypt_set_password(g_crypt, dfslib_utf8_string(&str, MINERS_PWD, strlen(MINERS_PWD)));

    for(i = 0; i < 128; ++i) {
        sector0[i] = SECTOR0_BASE + i * SECTOR0_OFFSET;
    }

    for(i = 0; i < 128; ++i) {
        dfslib_crypt_set_sector0(g_crypt, sector0);
        dfslib_encrypt_sector(g_crypt, sector0, SECTOR0_BASE + i * SECTOR0_OFFSET);
    }

    return 0;
}
