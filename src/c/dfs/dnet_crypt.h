#ifndef DNET_CRYPT_H_INCLUDED
#define DNET_CRYPT_H_INCLUDED 
#include <stdint.h>
#include "dfslib_crypt.h"
#include "dfsrsa.h"
#ifdef __cplusplus
extern "C" {
#endif

#define DNET_KEY_SIZE	4096
#define DNET_KEYLEN	((DNET_KEY_SIZE * 2) / (sizeof(dfsrsa_t) * 8))

struct dnet_key {
    dfsrsa_t key[DNET_KEYLEN];
};

#define PWDLEN	    64
#define SECTOR_LOG  9
#define SECTOR_SIZE (1 << SECTOR_LOG)
#define KEYLEN_MIN	(DNET_KEYLEN / 4)

//extern int crypt_start(struct dfslib_crypt* g_crypt);
extern int crypt_start(void);

#ifdef __cplusplus
};
#endif

#endif
