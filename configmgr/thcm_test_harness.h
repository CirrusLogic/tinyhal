/*
 * Copyright (C) 2020 Cirrus Logic, Inc. and
 *                    Cirrus Logic International Semiconductor Ltd.
 *                    All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef THCM_TEST_HARNESS_H

#ifdef THCM_TEST_HARNESS_BUILD
extern void* harness_malloc(size_t n, int line);
extern void* harness_calloc(size_t n, size_t m, int line);
extern void* harness_realloc(void* p, size_t n, int line);
extern char* harness_strdup(const char* s, int line);
extern int harness_asprintf(char** dest, const char* fmt, int c, const char* s1,
                            const char* s2, int line);
extern void harness_free(void* p);
#define malloc(x) harness_malloc(x, __LINE__)
#define calloc(n,m) harness_calloc(n, m, __LINE__)
#define realloc(p,n) harness_realloc(p, n,__LINE__)
#define strdup(s) harness_strdup(s, __LINE__)
#define asprintf(d, f, c, p, q) harness_asprintf(d, f, c, p, q, __LINE__)
#define free(x) harness_free(x)
#endif /* ifdef THCM_TEST_HARNESS_BUILD */

#endif /* ifndef THCM_TEST_HARNESS_H */
