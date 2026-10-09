/* -*- C++ -*-
 * 
 *  graphics_mmx.cpp - graphics routines using X86 MMX cpu functionality
 *
 *  Copyright (c) 2009 "Uncle" Mion Sonozaki
 *
 *  UncleMion@gmail.com
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

// Based upon routines provided by Roto

#ifdef USE_X86_GFX

#include <mmintrin.h>
#include <math.h>
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#include "graphics_common.h"


void imageFilterMean_MMX(unsigned char *src1, unsigned char *src2, unsigned char *dst, int length)
{
    int i = 0;

    // Compute first few values so we're on a 8-byte boundary in dst
    for (; !is_aligned(dst + i, 8) && (i < length); i++) {
        dst[i] = mean_pixel(src1[i], src2[i]);
    }

    // Do bulk of processing using MMX (find the mean of 8 8-bit unsigned integers, with saturation)
    __m64 mask = _mm_set1_pi8(0x7F);
    for (; i < length - 7; i += 8) {
        __m64 s1 = *((__m64*)(src1 + i));
        s1 = _mm_srli_pi16(s1, 1);
        s1 = _mm_and_si64(s1, mask);
        __m64 s2 = *((__m64*)(src2 + i));
        s2 = _mm_srli_pi16(s2, 1);
        s2 = _mm_and_si64(s2, mask);
        __m64* d = (__m64*)(dst + i);
        *d = _mm_adds_pu8(s1, s2);
    }
    _mm_empty();

    // If any bytes are left over, deal with them individually
    for (; i < length; i++) {
        dst[i] = mean_pixel(src1[i], src2[i]);
    }
}


void imageFilterAddTo_MMX(unsigned char *dst, unsigned char *src, int length)
{
    int i = 0;

    // Compute first few values so we're on a 8-byte boundary in dst
    for (; !is_aligned(dst + i, 8) && (i < length); i++) {
        addto_pixel(dst[i], src[i]);
    }

    // Do bulk of processing using MMX (add 8 8-bit unsigned integers, with saturation)
    for (; i < length - 7; i += 8) {
        __m64* s = (__m64*)(src + i);
        __m64* d = (__m64*)(dst + i);
        *d = _mm_adds_pu8(*d, *s);
    }
    _mm_empty();

    // If any bytes are left over, deal with them individually
    for (; i < length; i++) {
        addto_pixel(dst[i], src[i]);
    }
}


void imageFilterSubFrom_MMX(unsigned char *dst, unsigned char *src, int length)
{
    int i = 0;

    // Compute first few values so we're on a 8-byte boundary in dst
    for (; !is_aligned(dst + i, 8) && (i < length); i++) {
        subfrom_pixel(dst[i], src[i]);
    }

    // Do bulk of processing using MMX (sub 8 8-bit unsigned integers, with saturation)
    for (; i < length - 7; i += 8) {
        __m64* s = (__m64*)(src + i);
        __m64* d = (__m64*)(dst + i);
        *d = _mm_subs_pu8(*d, *s);
    }
    _mm_empty();

    // If any bytes are left over, deal with them individually
    for (; i < length; i++) {
        subfrom_pixel(dst[i], src[i]);
    }
}

#endif


