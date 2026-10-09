/* -*- C++ -*-
 *
 *  graphics_x86_common.h - shared code between SSE graphics backends
 *
 *  Copyright (c) 2021 TellowKrinkle
 *
 *  tellowkrinkle@gmail.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>
 *  or write to the Free Software Foundation, Inc.,
 *  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

#ifdef USE_X86_GFX

#include <SDL.h>
#include <immintrin.h>

#include "graphics_common.h"

/// 0x0000gg?? -> 0x00gg00gg
static HELPER_FN __m128i extractFromGTo16L(__m128i v) {
#ifdef __SSSE3__
    __m128i mask = _mm_setr_epi8(1, 0x80, 1, 0x80, 5, 0x80, 5, 0x80, 9, 0x80, 9, 0x80, 13, 0x80, 13, 0x80);
    return _mm_shuffle_epi8(v, mask);
#else
    __m128i shifted = _mm_srli_epi32(v, 8); // 0x000000gg
    return _mm_or_si128(shifted, _mm_slli_epi32(shifted, 16));
#endif
}

/// 0x????gg?? -> 0x000000gg
static HELPER_FN __m128i extractG(__m128i v) {
#ifdef __SSSE3__
    __m128i mask = _mm_setr_epi8(1, 0x80, 0x80, 0x80, 5, 0x80, 0x80, 0x80, 9, 0x80, 0x80, 0x80, 13, 0x80, 0x80, 0x80);
    return _mm_shuffle_epi8(v, mask);
#else
    __m128i shifted = _mm_srli_epi32(v, 8);
    return _mm_and_si128(shifted, _mm_set1_epi32(0xFF));
#endif
}

/// 0x000000bb -> 0x00bb00bb
static HELPER_FN __m128i extractBTo16L(__m128i v) {
#ifdef __SSSE3__
    __m128i mask = _mm_setr_epi8(0, 0x80, 0, 0x80, 4, 0x80, 4, 0x80, 8, 0x80, 8, 0x80, 12, 0x80, 12, 0x80);
    return _mm_shuffle_epi8(v, mask);
#else
    return _mm_or_si128(_mm_slli_epi32(v, 16), v);
#endif
}

static HELPER_FN void imageFilterBlend_SSE_Common(Uint32 *dst_buffer, Uint32 *src_buffer, Uint8 *alphap, int alpha, int length) {
    int n = length;

    // Compute first few values so we're on a 16-byte boundary in dst_buffer
    while( (((long)dst_buffer & 0xF) > 0) && (n > 0) ) {
        BLEND_PIXEL();
        --n; ++dst_buffer; ++src_buffer;
    }

    // Do bulk of processing using SSE2 (process 4 32bit (BGRA) pixels)
    // create basic bitmasks 0x00FF00FF, 0x000000FF
    __m128i bmask2 = _mm_set1_epi32(0x00FF00FF);
    while (n >= 4) {
        // alpha1 = ((src_argb >> 24) * alpha) >> 8
        __m128i a = _mm_set1_epi32(alpha);
        __m128i buf = _mm_loadu_si128((__m128i*)src_buffer);
        __m128i tmp = _mm_srli_epi32(buf, 24);
        a = _mm_mullo_epi16(a, tmp);
        // double-up alpha1 (0x0000vvxx -> 0x00vv00vv)
        a = extractFromGTo16L(a);
        // rb = (src_argb & bmask2) * alpha1
        tmp = _mm_and_si128(buf, bmask2);
        __m128i rb = _mm_mullo_epi16(a, tmp);
        // g = ((src_argb >> 8) & bmask) * alpha1
        tmp = extractG(buf);
        __m128i g = _mm_mullo_epi16(a, tmp);
        // alpha2 = alpha1 ^ bmask2
        a = _mm_xor_si128(a, bmask2);
        buf = _mm_load_si128((__m128i*)dst_buffer);
        // rb += (dst_argb & bmask2) * alpha2
        tmp = _mm_and_si128(buf, bmask2);
        tmp = _mm_mullo_epi16(a, tmp);
        rb = _mm_add_epi16(rb, tmp);
        // rb = 0xbbxxrrxx -> 0x00bb00rr
        rb = _mm_srli_epi16(rb, 8);
        // g += ((dst_argb >> 8) & bmask) * alpha2
        tmp = extractG(buf);
        tmp = _mm_mullo_epi16(a, tmp);
        g = _mm_add_epi32(g, tmp);
        // g = g & ~bmask2
        g = _mm_andnot_si128(bmask2, g);
        // dst_argb = rb | g
        tmp = _mm_or_si128(rb, g);
        _mm_store_si128((__m128i*)dst_buffer, tmp);

        n -= 4; src_buffer += 4; dst_buffer += 4; alphap += 16;
    }

    // If any pixels are left over, deal with them individually
    ++n;
    BASIC_BLEND();
}

static HELPER_FN bool alphaMaskBlend_SSE_Common(SDL_Surface* dst, SDL_Surface *s1, SDL_Surface *s2, SDL_Surface *mask_surface, const SDL_Rect& rect, Uint32 mask_value)
{
    if (mask_surface->w < 4) {
        return false;
    }

    int end_x = rect.x + rect.w;
    int end_y = rect.y + rect.h;
    int mask_height = mask_surface->h;
    int mask_width = mask_surface->w;

    int mask_off_base_y = rect.y % mask_surface->h;
    int mask_off_base_x = rect.x % mask_surface->w;
    for (int y = rect.y, my = mask_off_base_y; y < end_y; y++, my++) {
        if (my >= mask_height) { my = 0; }
        Uint32* s1p = getPointerToRow<Uint32>(s1, y);
        Uint32* s2p = getPointerToRow<Uint32>(s2, y);
        Uint32* dstp = getPointerToRow<Uint32>(dst, y);
        Uint32* mask_buf = getPointerToRow<Uint32>(mask_surface, my);

        int x = rect.x, mx = mask_off_base_x;
        while (!is_aligned(dstp + x, 16) && (x < end_x)) {
            dstp[x] = blendMaskOnePixel(s1p[x], s2p[x], mask_buf[mx], mask_value);
            x++, mx++;
            if (mx >= mask_width) { mx = 0; }
        }
        __m128i mask_value_v = _mm_set1_epi32(mask_value);
        __m128i mask_000000ff = _mm_set1_epi32(0x000000FF);
        __m128i mask_00ff00ff = _mm_set1_epi32(0x00FF00FF);
        while (x < (end_x - 3)) {
            __m128i s1v = _mm_loadu_si128((__m128i*)(s1p + x));
            __m128i s2v = _mm_loadu_si128((__m128i*)(s2p + x));
            __m128i mskv;
            if (__builtin_expect(mx + 3 < mask_width, true)) {
                mskv = _mm_loadu_si128((__m128i*)(mask_buf + mx));
            } else {
                __attribute__((aligned(16))) Uint32 tmp[4];
                for (int i = 0; i < 4; i++) {
                    if (mx + i < mask_width) {
                        tmp[i] = mask_buf[mx + i];
                    } else {
                        tmp[i] = mask_buf[mx + i - mask_width];
                    }
                }
                mskv = _mm_load_si128((__m128i*)tmp);
            }
            mskv = _mm_and_si128(mskv, mask_000000ff);
            __m128i mask2 = _mm_subs_epu16(mask_value_v, mskv);
            mask2 = _mm_min_epi16(mask2, mask_000000ff); // min(mask2, 0xFF)
#ifdef __clang__
            asm("":"+x"(mask2)::); // clang optimization makes things worse, block it
#endif
            mask2 = extractBTo16L(mask2); // Spread alpha for multiplying (0x00aa00aa)
            __m128i mask1 = _mm_xor_si128(mask2, mask_00ff00ff);
            // out_rb = ((s1v & rbmask) * mask1 + (s2v & rbmask) * mask2) >> 8
            __m128i s1v_rb = _mm_mullo_epi16(mask1, _mm_and_si128(s1v, mask_00ff00ff));
            __m128i s2v_rb = _mm_mullo_epi16(mask2, _mm_and_si128(s2v, mask_00ff00ff));
            __m128i out_rb = _mm_srli_epi16(_mm_add_epi16(s1v_rb, s2v_rb), 8);
            // out_g = (((s1v & gmask) >> 8) * mask1 + ((s2v & gmask) >> 8) * mask2) & gmask
            __m128i s1v_g = _mm_mullo_epi16(mask1, extractG(s1v));
            __m128i s2v_g = _mm_mullo_epi16(mask2, extractG(s2v));
            __m128i out_g = _mm_andnot_si128(mask_00ff00ff, _mm_add_epi16(s1v_g, s2v_g));
            _mm_store_si128((__m128i*)(dstp + x), _mm_or_si128(out_rb, out_g));

            x += 4;
            mx += 4;
            if (mx >= mask_width) { mx -= mask_width; }
        }
        while (x < end_x) {
            dstp[x] = blendMaskOnePixel(s1p[x], s2p[x], mask_buf[mx], mask_value);
            x++, mx++;
            if (mx >= mask_width) { mx = 0; }
        }
    }
    return true;
}

static HELPER_FN void alphaMaskBlendConst_SSE_Common(SDL_Surface* dst, SDL_Surface *s1, SDL_Surface *s2, const SDL_Rect& rect, Uint32 mask_value)
{
    int end_x = (rect.x + rect.w) * 4;
    int end_y = rect.y + rect.h;
    for (int y = rect.y; y < end_y; y++) {
        char* s1p = getPointerToRow<char>(s1, y);
        char* s2p = getPointerToRow<char>(s2, y);
        char* dstp = getPointerToRow<char>(dst, y);

        int x = rect.x * 4;
        for (; !is_aligned(dstp + x, 16) && (x < end_x); x += 4) {
            *(Uint32*)(dstp + x) = blendMaskOnePixel(*(Uint32*)(s1p + x), *(Uint32*)(s2p + x), 0, mask_value);
        }
        __m128i mask_000000ff = _mm_set1_epi32(0x000000FF);
        __m128i mask_00ff00ff = _mm_set1_epi32(0x00FF00FF);
        __m128i mask2 = _mm_set1_epi16(mask_value);
        __m128i mask1 = _mm_xor_si128(mask2, mask_00ff00ff);
        for (; x < (end_x - 15); x += 16) {
            __m128i s1v = _mm_loadu_si128((__m128i*)(s1p + x));
            __m128i s2v = _mm_loadu_si128((__m128i*)(s2p + x));
            // out_rb = ((s1v & rbmask) * mask1 + (s2v & rbmask) * mask2) >> 8
            __m128i s1v_rb = _mm_mullo_epi16(mask1, _mm_and_si128(s1v, mask_00ff00ff));
            __m128i s2v_rb = _mm_mullo_epi16(mask2, _mm_and_si128(s2v, mask_00ff00ff));
            __m128i out_rb = _mm_srli_epi16(_mm_add_epi16(s1v_rb, s2v_rb), 8);
            // out_g = (((s1v & gmask) >> 8) * mask1 + ((s2v & gmask) >> 8) * mask2) & gmask
            __m128i s1v_g = _mm_mullo_epi16(mask1, extractG(s1v));
            __m128i s2v_g = _mm_mullo_epi16(mask2, extractG(s2v));
            __m128i out_g = _mm_andnot_si128(mask_00ff00ff, _mm_add_epi16(s1v_g, s2v_g));
            _mm_store_si128((__m128i*)(dstp + x), _mm_or_si128(out_rb, out_g));
        }
        for (; x < end_x; x += 4) {
            *(Uint32*)(dstp + x) = blendMaskOnePixel(*(Uint32*)(s1p + x), *(Uint32*)(s2p + x), 0, mask_value);
        }
    }
}

#endif
