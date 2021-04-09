#include <jni.h>
#include <limits.h>
#include <iostream>
#include "randomx.h"
#include "c_threads.h"

#define MAX_RX_KEY_LEN 512

//randomx种子和cache的信息
typedef struct seedinfo {
	randomx_cache *si_cache;
	randomx_dataset *si_dataset;
	unsigned long si_start;
	unsigned long si_count;
} seedinfo;

static CTHR_THREAD_RTYPE rx_seedthread(void *arg) {
	seedinfo *si = (seedinfo*)arg;
	printf("randomx init dataset begin start %lu count %lu \n",si->si_start,si->si_count);
	randomx_init_dataset(si->si_dataset, si->si_cache, si->si_start, si->si_count);
	printf("randomx init dataset end   start %lu count %lu \n",si->si_start,si->si_count);
	CTHR_THREAD_RETURN;
}

extern "C"
JNIEXPORT jlong JNICALL Java_io_xdag_crypto_jni_RandomX_allocCache(
        JNIEnv *env,
        jobject *obj) {
    randomx_flags flags = randomx_get_flags();
    randomx_cache* cache=randomx_alloc_cache(flags);
    std::cout << "alloc randomx cache " << cache << std::endl;
    return (jlong)cache;
}

extern "C"
JNIEXPORT void JNICALL Java_io_xdag_crypto_jni_RandomX_initCache(
        JNIEnv *env,
        jobject *obj,
        jlong jcache,
        jbyteArray jkey,
        jint jlen) {

    char key[MAX_RX_KEY_LEN] = {0};
    env->GetByteArrayRegion(jkey,0,jlen,(jbyte*)key);
    randomx_cache* cache=(randomx_cache*)jcache;
    randomx_init_cache(cache,key,jlen);
    std::cout << "init randomx cache " << cache << " with seed " << key << std::endl;
    return;
}

extern "C"
JNIEXPORT void JNICALL Java_io_xdag_crypto_jni_RandomX_releaseCache(
        JNIEnv *env,
        jobject *obj,
        jlong jcache) {
    randomx_cache* cache=(randomx_cache*)jcache;
    randomx_release_cache(cache);
    std::cout << "release randomx cache " << cache << std::endl;
    return;
}

extern "C"
JNIEXPORT jlong JNICALL Java_io_xdag_crypto_jni_RandomX_allocDataSet(
        JNIEnv *env,
        jobject *obj) {
    randomx_flags flags = randomx_get_flags();
    randomx_dataset* dataset=randomx_alloc_dataset(flags);
    std::cout << "alloc randomx dataset " << dataset << std::endl;
    return (jlong)dataset;
}

extern "C"
JNIEXPORT void JNICALL Java_io_xdag_crypto_jni_RandomX_initDataSet(
        JNIEnv *env,
        jobject *obj,
        jlong jcache,
        jlong jdataset,
        jint jminers) {
    randomx_cache* rs_cache=(randomx_cache*)jcache;
    randomx_dataset* rx_dataset=(randomx_dataset*)jdataset;

    if (jminers > 1) {
        // 平均分配给每一个线程的dataset item
        unsigned long delta = randomx_dataset_item_count() / jminers;
        unsigned long start = 0;
        int i;
        seedinfo *si;
        CTHR_THREAD_TYPE *st;

        si = (seedinfo*)malloc(jminers * sizeof(seedinfo));
        if (si == NULL){
            std::cout << "Couldn't allocate RandomX mining threadinfo" << std::endl;
            //TODO: throw exception here
        }

        st = (CTHR_THREAD_TYPE *)malloc(jminers * sizeof(CTHR_THREAD_TYPE));
        if (st == NULL) {
            free(si);
            std::cout << "Couldn't allocate RandomX mining threadlist" << std::endl;
            //TODO: throw exception here
        }

        // 记录每个线程的dataset item的起始索引，item个数
        for (i=0; i<jminers-1; i++) {
            si[i].si_cache = rs_cache;
            si[i].si_dataset = rx_dataset;
            si[i].si_start = start;   //起始索引
            si[i].si_count = delta;   //结束个数
            start += delta;
        }
        // 最后一个线程使用dataset里面剩余的所有的 item
        si[i].si_cache = rs_cache;
        si[i].si_dataset = rx_dataset;
        si[i].si_start = start;
        si[i].si_count = randomx_dataset_item_count() - start;

        // 开启多线程，每个线程都初始化自己的dataset
        for (i=0; i<jminers; i++) {
            CTHR_THREAD_CREATE(st[i], rx_seedthread, &si[i]);
        }

        // 等待线程初始化的结束
        for (i=0; i<jminers; i++) {
            CTHR_THREAD_JOIN(st[i]);
        }
        free(st);
        free(si);
    } else {
        // 如果只有一个线程则使用dataset里面所有的item
        randomx_init_dataset(rx_dataset, rs_cache, 0, randomx_dataset_item_count());
    }

    std::cout << "init randomx dataset " << rx_dataset << std::endl;
    return;
}

extern "C"
JNIEXPORT void JNICALL Java_io_xdag_crypto_jni_RandomX_releaseDataSet(
        JNIEnv *env,
        jobject *obj,
        jlong jdataset) {

    randomx_dataset* rx_dataset=(randomx_dataset*)jdataset;
    randomx_release_dataset(rx_dataset);
    std::cout << "release randomx dataset " << rx_dataset << std::endl;
}

extern "C"
JNIEXPORT jlong JNICALL Java_io_xdag_crypto_jni_RandomX_createVm(
        JNIEnv *env,
        jobject *obj,
        jlong jcache,
        jlong jdataset,
        jint jminers) {
    randomx_cache* rs_cache=(randomx_cache*)jcache;
    randomx_dataset* rx_dataset=(randomx_dataset*)jdataset;
    randomx_flags flags = randomx_get_flags();
    randomx_vm *rx_vm = randomx_create_vm(flags | RANDOMX_FLAG_LARGE_PAGES, rs_cache, rx_dataset);
    if(rx_vm == NULL) {
        std::cout <<"Couldn't use largePages for RandomX VM" << std::endl;
        rx_vm = randomx_create_vm(flags, rs_cache, rx_dataset);
    }
    if(rx_vm == NULL) {
        //TODO: try full memory flag
        flags = RANDOMX_FLAG_DEFAULT;
        rx_vm = randomx_create_vm(flags, rs_cache, rx_dataset);
    }
    if (rx_vm == NULL){
        std::cout << "Couldn't allocate RandomX VM" << std::endl;
        //TODO: throw an exception
        return 0;
    }
    std::cout << "alloc randomx vm " << rx_vm << std::endl;
    return (jlong)rx_vm;
}

extern "C"
JNIEXPORT void JNICALL Java_io_xdag_crypto_jni_RandomX_destroyVm(
        JNIEnv *env,
        jobject *obj,
        jlong jrxvm) {
    randomx_vm* rx_vm=(randomx_vm*)jrxvm;
    randomx_destroy_vm(rx_vm);
    std::cout << "randomx destroy vm " << rx_vm << std::endl;
}

extern "C"
JNIEXPORT jbyteArray JNICALL Java_io_xdag_crypto_jni_RandomX_calculateHash(
        JNIEnv *env,
        jobject *obj,
        jlong jrxvm,
        jbyteArray jdata,
        jint jlen){
    uint8_t hash[32]={0};
    uint8_t* data=(uint8_t*)malloc(jlen);
    env->GetByteArrayRegion(jdata,0,jlen,(jbyte*)data);
    randomx_vm* rx_vm=(randomx_vm*)jrxvm;
    randomx_calculate_hash(rx_vm, data, jlen, hash);
    jbyteArray outputHash=env->NewByteArray(32);
    env->SetByteArrayRegion(outputHash,0,32,(jbyte*)hash);
    free(data);
    return outputHash;
}




