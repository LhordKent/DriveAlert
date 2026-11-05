#pragma once
// Host test adapter uses Windows CNG, never a mock hash. Production uses Mbed TLS.
#include <windows.h>
#include <bcrypt.h>
#include <vector>
#define MBEDTLS_MD_SHA256 1
struct mbedtls_md_info_t {};
inline const mbedtls_md_info_t *mbedtls_md_info_from_type(int) { static mbedtls_md_info_t info; return &info; }
inline int mbedtls_md_hmac(const mbedtls_md_info_t *, const unsigned char *key, size_t keyLength,
    const unsigned char *message, size_t length, unsigned char *output) {
  BCRYPT_ALG_HANDLE algorithm = nullptr;
  BCRYPT_HASH_HANDLE hash = nullptr;
  if (BCryptOpenAlgorithmProvider(&algorithm, BCRYPT_SHA256_ALGORITHM, nullptr, BCRYPT_ALG_HANDLE_HMAC_FLAG) < 0) return -1;
  DWORD objectLength = 0, copied = 0;
  auto status = BCryptGetProperty(algorithm, BCRYPT_OBJECT_LENGTH, reinterpret_cast<PUCHAR>(&objectLength), sizeof(objectLength), &copied, 0);
  std::vector<unsigned char> object(objectLength);
  if (status >= 0) status = BCryptCreateHash(algorithm, &hash, object.data(), object.size(), const_cast<PUCHAR>(key), keyLength, 0);
  if (status >= 0) status = BCryptHashData(hash, const_cast<PUCHAR>(message), length, 0);
  if (status >= 0) status = BCryptFinishHash(hash, output, 32, 0);
  if (hash) BCryptDestroyHash(hash);
  BCryptCloseAlgorithmProvider(algorithm, 0);
  return status < 0 ? -1 : 0;
}
