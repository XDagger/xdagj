#include "crc.h"
#include "dfslib_crypt.h"
#include "dfslib_random.h"
#include "dnet_crypt.h"
#include "dfsrsa.h"
#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#define DNET_KEY_SIZE	4096
//#define DNET_KEYLEN	32
#define DNET_KEYLEN	((DNET_KEY_SIZE * 2) / (sizeof(dfsrsa_t) * 8))

#define MINERS_PWD             "minersgonnamine"
#define SECTOR0_BASE           0x1947f3acu
#define SECTOR0_OFFSET         0x82e9d1b5u
#define SECTOR_LOG  9
#define SECTOR_SIZE (1 << SECTOR_LOG)
static int g_keylen = 0;

struct dnet_packet_header {
    uint8_t type;
    uint8_t ttl;
    uint16_t length;
    uint32_t crc32;
};

struct xsector {
    union {
        uint8_t byte[SECTOR_SIZE];
        uint32_t word[SECTOR_SIZE / sizeof(uint32_t)];
        struct xsector *next;
        struct dnet_packet_header head;
    };
};

struct xdnet_keys {
    struct dnet_key priv, pub;
    struct xsector sect0_encoded, sect0;
};

struct dnet_keys {
	struct dnet_key priv;
	struct dnet_key pub;
};

static struct xdnet_keys g_test_xkeys;
static struct dnet_keys *g_dnet_keys = 0;
static struct dnet_keys *g_dnet_user_keys = 0;
static struct dfslib_crypt *g_test_crypt = 0;
static struct dfslib_crypt *g_dnet_user_crypt = 0;
static struct dfslib_crypt *g_crypt = 0;

//crypt start
int crypt_start(void)
{
    struct dfslib_string str;
    uint32_t sector0[128];
    int i;
    if(g_crypt == 0){
        g_crypt = (struct dfslib_crypt*)malloc(sizeof(struct dfslib_crypt));
    }
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

static void dnet_sector_to_password(uint32_t sector[SECTOR_SIZE / 4], char password[PWDLEN + 1]) {
    int i;
    for (i = 0; i < PWDLEN / 8; ++i) {
        unsigned crc = crc_of_array((unsigned char *)(sector + i * SECTOR_SIZE / 4 / (PWDLEN / 8)), SECTOR_SIZE / (PWDLEN / 8));
        sprintf(password + 8 * i, "%08X", crc);
    }
}

static void dnet_make_key(dfsrsa_t *key, int keylen)
{
	unsigned i;
	for(i = keylen; i < DNET_KEYLEN; i += keylen) {
		memcpy(key + i, key, keylen * sizeof(dfsrsa_t));
	}
}

//生成random
static void dnet_random_sector(uint32_t sector[SECTOR_SIZE / 4]) {
    char password[PWDLEN + 1] = "Iyf&%d#$jhPo_t|3fgd+hf(s@;)F5D7gli^kjtrd%.kflP(7*5gt;Y1sYRC4VGL&";
	int i, j;
    for (i = 0; i < 3; ++i) {
        struct dfslib_string str;
        dfslib_utf8_string(&str, password, PWDLEN);
        dfslib_random_sector(sector, 0, &str, &str);
		for (j = KEYLEN_MIN / 8; j <= SECTOR_SIZE / 4; j += KEYLEN_MIN / 8)
			sector[j - 1] &= 0x7FFFFFFF;
        if (i == 2) break;
		dfsrsa_crypt((dfsrsa_t *)sector, SECTOR_SIZE / sizeof(dfsrsa_t), g_dnet_keys->priv.key, DNET_KEYLEN);
        dnet_sector_to_password(sector, password);
    }
}

int dnet_generate_random_array(void *array, unsigned long size) {
	uint32_t sector[SECTOR_SIZE / 4];
	unsigned long i;
	if (size < 4 || size & (size - 1)) return -1;
	if (size >= 512) {
		for (i = 0; i < size; i += 512) dnet_random_sector((uint32_t *)((uint8_t *)array + i));
	} else {
		dnet_random_sector(sector);
		for (i = 0; i < size; i += 4) {
			*(uint32_t *)((uint8_t *)array + i) = crc_of_array((unsigned char *)sector + i * 512 / size, 512 / size);
		}
	}
	return 0;
}

//todo add by myron
static int dnet_detect_keylen(dfsrsa_t *key, int keylen) {

	if (g_keylen && (key == g_dnet_keys->priv.key || key == g_dnet_keys->pub.key))
		return g_keylen;
	while (keylen >= 8) {
		if (memcmp(key, key + keylen / 2, keylen * sizeof(dfsrsa_t) / 2)) break;
		keylen /= 2;
	}
	return keylen;
}

static int set_user_crypt(struct dfslib_string *pwd) {
	uint32_t sector0[128];
	int i;
	if(g_dnet_user_crypt == 0){
	    g_dnet_user_crypt = (struct dfslib_crypt*)malloc(sizeof(struct dfslib_crypt));
	}
	if (!g_dnet_user_crypt) return -1;
	//置0
	memset(g_dnet_user_crypt->pwd, 0, sizeof(g_dnet_user_crypt->pwd));
	dfslib_crypt_set_password(g_dnet_user_crypt, pwd);
	for (i = 0; i < 128; ++i) sector0[i] = 0x4ab29f51u + i * 0xc3807e6du;
	for (i = 0; i < 128; ++i) {
		dfslib_crypt_set_sector0(g_dnet_user_crypt, sector0);
		dfslib_encrypt_sector(g_dnet_user_crypt, sector0, 0x3e9c1d624a8b570full + i * 0x9d2e61fc538704abull);
	}
	return 0;
}

static int dnet_test_keys(void) {
    uint32_t src[SECTOR_SIZE / 4], dest[SECTOR_SIZE / 4];
    dnet_random_sector(src);
    memcpy(dest, src, SECTOR_SIZE);
	if (dfsrsa_crypt((dfsrsa_t *)dest, SECTOR_SIZE / sizeof(dfsrsa_t), g_dnet_keys->priv.key, DNET_KEYLEN)) return 1;
	if (dfsrsa_crypt((dfsrsa_t *)dest, SECTOR_SIZE / sizeof(dfsrsa_t), g_dnet_keys->pub.key, DNET_KEYLEN)) return 2;
	if (memcmp(dest, src, SECTOR_SIZE)) return 3;
    memcpy(dest, src, SECTOR_SIZE);
	if (dfsrsa_crypt((dfsrsa_t *)dest, SECTOR_SIZE / sizeof(dfsrsa_t), g_dnet_keys->pub.key, DNET_KEYLEN)) return 4;
	if (dfsrsa_crypt((dfsrsa_t *)dest, SECTOR_SIZE / sizeof(dfsrsa_t), g_dnet_keys->priv.key, DNET_KEYLEN)) return 5;
	if (memcmp(dest, src, SECTOR_SIZE)) return 6;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL Java_io_xdag_crypto_jni_Native_load_1dnet_1keys(
        JNIEnv *env,
        jobject *obj,
        jbyteArray keybytes,
        jint length) {

    char buf[3072] = {0};

    env->GetByteArrayRegion(keybytes,0,length,(jbyte*)buf);
    memcpy(&g_test_xkeys,buf,sizeof(struct xdnet_keys));

    return 3072;
}

extern "C"
JNIEXPORT jint JNICALL Java_io_xdag_crypto_jni_Native_dnet_1crypt_1init(
        JNIEnv *env,
        jobject *obj) {

    char password[PWDLEN + 1];
    struct dfslib_string str;
     if(g_test_crypt == 0) {
            g_test_crypt = (struct dfslib_crypt*)malloc(sizeof(struct dfslib_crypt));
        }
     if(g_dnet_user_crypt == 0) {
            g_dnet_user_crypt = (struct dfslib_crypt*)malloc(sizeof(struct dfslib_crypt));
        }
     if(g_dnet_keys == 0) {
            g_dnet_keys = (struct dnet_keys*)malloc(sizeof(struct dnet_keys));
        }

    memset(g_test_crypt,0,sizeof(struct dfslib_crypt));
    memset(g_dnet_user_crypt,0,sizeof(struct dfslib_crypt));
    memset(g_dnet_keys,0,sizeof(struct dnet_keys));

    if (crc_init()) {
        return -1;
    }
    
    dnet_sector_to_password(g_test_xkeys.sect0.word, password);
    //为密码做置换操作
    dfslib_crypt_set_password(g_test_crypt, dfslib_utf8_string(&str, password, PWDLEN));
    //加密密码到sector0
    dfslib_crypt_set_sector0(g_test_crypt, g_test_xkeys.sect0.word);

    return 0;
}

extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_dfslib_1encrypt_1byte_1sector(
        JNIEnv *env,
        jobject *obj,
        jbyteArray raw,
        jint length,
        jlong sectorNo) {

    //LOGI("get raw byte length is %d",length);

    //加密数据
    char buf[512] = {0};
    
    env->GetByteArrayRegion(raw,0,512,(jbyte*)buf);

    dfslib_encrypt_sector(g_test_crypt,(dfs32*)buf, (unsigned long long)sectorNo);

    jbyteArray jba = env->NewByteArray(512);
    env->SetByteArrayRegion(jba,0, 512, (jbyte*)buf);

    return jba;
}

extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_dfslib_1uncrypt_1byte_1sector(
        JNIEnv *env,
        jobject *obj,
        jbyteArray encrypted,
        jint length,
        jlong sectorNo) {

    //LOGI("get encrypted byte length is %d",length);

    char buf[512] = {0};
    env->GetByteArrayRegion(encrypted,0,512,(jbyte*)buf);

    //解密数据
    dfslib_uncrypt_sector(g_test_crypt,(dfs32*)buf,(unsigned long long)sectorNo);
    //LOGI("uncrypted data is %s ",buf);

    jbyteArray jba = env->NewByteArray(512);
    env->SetByteArrayRegion(jba,0,512,(jbyte*)buf);

    return jba;
}

extern "C"
JNIEXPORT jlong JNICALL Java_io_xdag_crypto_jni_Native_get_1user_1dnet_1crypt(
        JNIEnv *env,
        jobject *obj) {

    return (jlong)g_dnet_user_crypt;
}

extern "C"
JNIEXPORT jlong JNICALL Java_io_xdag_crypto_jni_Native_get_1dnet_1keys(
        JNIEnv *env,
        jobject *obj) {

    return (jlong)g_dnet_keys;
}

extern "C"
JNIEXPORT jint JNICALL Java_io_xdag_crypto_jni_Native_set_1user_1dnet_1crypt(
        JNIEnv *env,
        jobject *obj,
        jstring input_pwd) {

    jboolean isCopy=JNI_TRUE;
    struct dfslib_string str;
    uint32_t sector0[128];
    const char* pwd = env->GetStringUTFChars(input_pwd,&isCopy);

    dfslib_utf8_string(&str, pwd, strlen(pwd));
    dfslib_crypt_set_password(g_dnet_user_crypt, &str);
    for(int i = 0; i < 128; ++i) {
    	sector0[i] = 0x4ab29f51u + i * 0xc3807e6du;
    }

    for(int i = 0; i < 128; ++i) {
        dfslib_crypt_set_sector0(g_dnet_user_crypt, sector0);
        dfslib_encrypt_sector(g_dnet_user_crypt, sector0, 0x3e9c1d624a8b570full + i * 0x9d2e61fc538704abull);
    }

    env -> ReleaseStringUTFChars(input_pwd, pwd);
    return 0;
}

extern "C"
JNIEXPORT void JNICALL Java_io_xdag_crypto_jni_Native_set_1user_1random(
        JNIEnv *env,
        jobject *obj,
        jstring input_random_keys) {

    jboolean isCopy=JNI_TRUE;
    struct dfslib_string str;

    const char* random_keys = env->GetStringUTFChars(input_random_keys,&isCopy);
    dfslib_random_fill(g_dnet_keys->pub.key, DNET_KEYLEN * sizeof(dfsrsa_t), 0, dfslib_utf8_string(&str, random_keys, strlen(random_keys)));
    env -> ReleaseStringUTFChars(input_random_keys, random_keys);
}

extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_make_1dnet_1keys(
        JNIEnv *env,
        jobject *obj,
        jint keylen) {

    dfsrsa_keygen(g_dnet_keys->priv.key, g_dnet_keys->pub.key, keylen);
    dnet_make_key(g_dnet_keys->priv.key, keylen);
    dnet_make_key(g_dnet_keys->pub.key, keylen);

    if(g_dnet_user_crypt) {
        for(int i = 0; i < 4; ++i) {
            dfslib_encrypt_sector(g_dnet_user_crypt, (uint32_t *)g_dnet_keys + 128 * i, ~(uint64_t)i);
        }
    }

    jbyteArray jba = env->NewByteArray(sizeof(dnet_keys));
    env->SetByteArrayRegion(jba,0,sizeof(dnet_keys),(jbyte*)g_dnet_keys);

    if(g_dnet_user_crypt) {
        for(int i = 0; i < 4; ++i) {
            dfslib_uncrypt_sector(g_dnet_user_crypt, (uint32_t *)g_dnet_keys + 128 * i, ~(uint64_t)i);
        }
    }

    //dnet keys returned to java layer and written by java's file stream
    return jba;
}


//Java_io_xdag_crypto_jni_Native_dfslib_1encrypt_1byte_1sector
extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_encrypt_1wallet_1key(
        JNIEnv *env,
        jobject *obj,
        jbyteArray privkey,
        jint n) {

    uint32_t privkey_bytes[8] = {0};
    env->GetByteArrayRegion(privkey,0,8 * sizeof(uint32_t),(jbyte*)privkey_bytes);

    //8 * 32
    dfslib_encrypt_array(g_dnet_user_crypt, privkey_bytes, 8, n);

    jbyteArray jba = env->NewByteArray(sizeof(privkey_bytes));
    env->SetByteArrayRegion(jba,0,sizeof(privkey_bytes),(jbyte*)privkey_bytes);
    return jba;
}

//Java_io_xdag_crypto_jni_Native
extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_uncrypt_1wallet_1key(
        JNIEnv *env,
        jobject *obj,
        jbyteArray privkey,
        jint n) {

    uint32_t privkey_bytes[8] = {0};
    env->GetByteArrayRegion(privkey,0,8 * sizeof(uint32_t),(jbyte*)privkey_bytes);

    //8 * 32
    dfslib_uncrypt_array(g_dnet_user_crypt, privkey_bytes, 8, n);

    jbyteArray jba = env->NewByteArray(sizeof(privkey_bytes));
    env->SetByteArrayRegion(jba,0,sizeof(privkey_bytes),(jbyte*)privkey_bytes);
    return jba;
}

//generate_random_array
extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_generate_1random_1array(
        JNIEnv *env,
        jobject *obj,
        jbyteArray array,
        jint size) {

    uint32_t array_bytes[8] = {0};
    env->GetByteArrayRegion(array,0,8 * sizeof(uint32_t),(jbyte*)array_bytes);

    //8*32
    dnet_generate_random_array(array_bytes,size);

    jbyteArray jba = env->NewByteArray(sizeof(array_bytes));
    env->SetByteArrayRegion(jba,0,sizeof(array_bytes),(jbyte*)array_bytes);
    return jba;
}

extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_generate_1random_1bytes(
        JNIEnv *env,
        jobject *obj,
        jbyteArray array,
        jint size) {

    uint8_t array_bytes[8] = {0};
    env->GetByteArrayRegion(array,0,8 * sizeof(uint8_t),(jbyte*)array_bytes);

    //8*1
    dnet_generate_random_array(array_bytes,size);

    jbyteArray jba = env->NewByteArray(sizeof(array_bytes));
    env->SetByteArrayRegion(jba,0,sizeof(array_bytes),(jbyte*)array_bytes);
    return jba;
}

/*
 * Class:     io_xdag_Myron_jni_MyJni
 * Method:    dfslib_uncrypt_array
 * Signature: ([BIJ)[B
 */
 extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_dfslib_1uncrypt_1array(
        JNIEnv *env,
        jobject *obj,
        jbyteArray encrypt,
        jint nfiled,
        jlong sectorNo) {
        int size = 32 * nfiled;
        char buf[512] = {0};


        env -> GetByteArrayRegion(encrypt, 0 , size, (jbyte*)buf);

        int pos = 0;
        //解密数据这里要循环解密
        for (int i = 0; i< nfiled ;i++) {
          dfslib_uncrypt_array(g_crypt, (dfs32*)(buf+ pos), 8, sectorNo++);
          pos = pos+32;
        }

        jbyteArray jba = env -> NewByteArray(size);

          //env->SetByteArrayRegion(jba,0,512,(jbyte*)buf);
        env-> SetByteArrayRegion(jba, 0, size, (jbyte*)buf);

        return jba;
  }

/*
 * Class:     io_xdag_Myron_jni_MyJni
 * Method:    dfslib_encrypt_array
 * Signature: ([BIJ)[B
 */
 extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_dfslib_1encrypt_1array
  (JNIEnv *env, jobject *obj, jbyteArray encrypt, jint nfiled, jlong sectorNo ){

          int size = 32 * nfiled;

          char buf[512] = {0};

          env -> GetByteArrayRegion(encrypt, 0 , size, (jbyte*)buf);


           int pos = 0;
          //解密数据这里要循环解密
          for (int i = 0; i< nfiled ;i++) {
            dfslib_encrypt_array(g_crypt, (dfs32*)(buf+ pos), 8, sectorNo++);
            pos = pos+32;
          }

          jbyteArray jba = env -> NewByteArray(size);

          env-> SetByteArrayRegion(jba, 0, size, (jbyte*)buf);

          return jba;
  }
  //开始写三个主要的函数

// JNIEXPORT void JNICALL Java_io_xdag_Myron_jni_MyJni_init_1g_1crypt
extern "C"
JNIEXPORT jint JNICALL Java_io_xdag_crypto_jni_Native_crypt_1start(
        JNIEnv *env,
        jobject *obj) {
      return crypt_start();
  }


extern "C"
JNIEXPORT void JNICALL Java_io_xdag_crypto_jni_Native_dfslib_1random_1init(
        JNIEnv *env, jobject *obj) {
        dfslib_random_init();
  }

extern "C"
JNIEXPORT void JNICALL Java_io_xdag_crypto_jni_Native_crc_1init(
        JNIEnv *env, jobject *obj) {
        crc_init();
  }

extern "C"
JNIEXPORT jint JNICALL Java_io_xdag_crypto_jni_Native_dnet_1detect_1keylen(
        JNIEnv *env, jobject *obj,jint len) {
        return dnet_detect_keylen(g_dnet_keys->pub.key,len);
  }


extern "C"
JNIEXPORT jint JNICALL Java_io_xdag_crypto_jni_Native_verify_1dnet_1key(
        JNIEnv *env, jobject *obj, jstring password, jbyteArray key) {


        char buf[2048] = {0};
        env -> GetByteArrayRegion(key, 0 , 2048, (jbyte*)buf);
        memcpy(g_dnet_keys, buf, 2048);


        jboolean isCopy=JNI_TRUE;
        struct dfslib_string str;
        uint32_t sector0[128];
        const char* pwd = env->GetStringUTFChars(password,&isCopy);
        dfslib_utf8_string(&str, pwd, strlen(pwd));

        g_keylen = dnet_detect_keylen(g_dnet_keys->pub.key,DNET_KEYLEN);

        if (dnet_test_keys()) {


        //----------------------------------------------------
        memset(g_dnet_user_crypt->pwd, 0, sizeof(g_dnet_user_crypt->pwd));
        dfslib_crypt_set_password(g_dnet_user_crypt, &str);
        //给sector0进行赋值  每一个都是固定的
        for (int i = 0; i < 128; ++i)
           {
                sector0[i] = 0x4ab29f51u + i * 0xc3807e6du;
           }
        for (int i = 0; i < 128; ++i) {  //128次加密？？
            dfslib_crypt_set_sector0(g_dnet_user_crypt, sector0);
            dfslib_encrypt_sector(g_dnet_user_crypt, sector0, 0x3e9c1d624a8b570full + i * 0x9d2e61fc538704abull);
        }
        if (g_dnet_user_crypt) {
            for (int i = 0; i < (sizeof(struct dnet_keys) >> 9); ++i) {
                dfslib_uncrypt_sector(g_dnet_user_crypt, (uint32_t *)g_dnet_keys + 128 * i, ~(uint64_t)i);
            }
        }

        //-----------------------------------------------------------------------------------------------------
        g_keylen = 0;
        g_keylen = dnet_detect_keylen(g_dnet_keys->pub.key, DNET_KEYLEN);

        env -> ReleaseStringUTFChars(password, pwd);

        }

        return -dnet_test_keys();
  }


extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_Native_general_1dnet_1key(
        JNIEnv *env, jobject *obj, jstring password, jstring random) {

        jbyteArray jba = env->NewByteArray(sizeof(dnet_keys));

        jboolean isCopy=JNI_TRUE;
        struct dfslib_string str;
        uint32_t sector0[128];
        const char* pwd = env->GetStringUTFChars(password,&isCopy);

        dfslib_utf8_string(&str, pwd, strlen(pwd));
        dfslib_crypt_set_password(g_dnet_user_crypt, &str);
        for(int i = 0; i < 128; ++i) {
            sector0[i] = 0x4ab29f51u + i * 0xc3807e6du;
        }

        for(int i = 0; i < 128; ++i) {
            dfslib_crypt_set_sector0(g_dnet_user_crypt, sector0);
            dfslib_encrypt_sector(g_dnet_user_crypt, sector0, 0x3e9c1d624a8b570full + i * 0x9d2e61fc538704abull);
        }


        //rand fill
         struct dfslib_string str1;

         const char* random_keys = env->GetStringUTFChars(random,&isCopy);
        dfslib_random_fill(g_dnet_keys->pub.key, DNET_KEYLEN * sizeof(dfsrsa_t), 0, dfslib_utf8_string(&str1, random_keys, strlen(random_keys)));



        dfsrsa_keygen(g_dnet_keys->priv.key, g_dnet_keys->pub.key, DNET_KEYLEN);
        dnet_make_key(g_dnet_keys->priv.key, DNET_KEYLEN);
        dnet_make_key(g_dnet_keys->pub.key, DNET_KEYLEN);

        if (g_dnet_user_crypt) {
            for (int i = 0; i < (sizeof(struct dnet_keys) >> 9); ++i)
                {
                    dfslib_encrypt_sector(g_dnet_user_crypt, (uint32_t *)g_dnet_keys + 128 * i, ~(uint64_t)i);
                }
                //把密钥返回
                env->SetByteArrayRegion(jba,0,sizeof(dnet_keys),(jbyte*)g_dnet_keys);
            }

        if (g_dnet_user_crypt) {
            for (int i = 0; i < (sizeof(struct dnet_keys) >> 9); ++i){
                dfslib_uncrypt_sector(g_dnet_user_crypt, (uint32_t *)g_dnet_keys + 128 * i, ~(uint64_t)i);
            }
        }

        env -> ReleaseStringUTFChars(password, pwd);
        env -> ReleaseStringUTFChars(random, random_keys);

        return jba;
  }