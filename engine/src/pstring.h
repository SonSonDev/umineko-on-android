// -*- c++ -*-
// Utility functions and classes for strings.

#include "bstrwrap.h"
typedef CBString pstring;

#ifndef PSTRING_H
#define PSTRING_H

#include <SDL.h>
#include <algorithm>
#include "encoding.h"

/// A piece of a pstring
/// @warning Holds a non-owning reference to the source string, convert it to a pstring before storing
struct psubstr {
private:
    static const pstring emptystr;
public:
    const pstring& source;
    int start;
    int end;

    /// psubstr empty string
    psubstr(): source(emptystr), start(0), end(0) {}
    /// psubstr covering a whole string
    psubstr(const pstring& whole): source(whole), start(0), end(whole.length()) {}
    /// psubstr from start to the end of a string
    psubstr(const pstring& source, int start): source(source), start(start), end(source.length()) {}
    /// psubstr from start to end
    psubstr(const pstring& source, int start, int end): source(source), start(start), end(end) {}

    /// Move the front of the substring forward by `amt`, clamped to [0, end]
    /// (Positive numbers shrink the substring, negataive numbers grow)
    psubstr adjustFront(int amt) const {
        int newstart = std::max(std::min(start + amt, end), 0);
        return psubstr(source, newstart, end);
    }

    /// Move the end of the string forward by `amt`, clamped to [start, strlen]
    /// (Positive numbers grow the substring, negative numbers shrink)
    psubstr adjustEnd(int amt) const {
        int newend = std::max(std::min(end + amt, source.length()), start);
        return psubstr(source, start, newend);
    }

    operator pstring() const {
        return source.midstr(start, end - start);
    }
};

/// Split the string on the first occurance of the given character
///
/// The first of the returned pair will be the section of the string up to but not including `delimiter`
/// The second will be the section of the string after `delimiter` (neither will contain `delimiter`)
/// If `str` doesn't contain `delimiter`, the second will be an empty substring
inline std::pair<psubstr, psubstr>
pstr_split_first(const pstring& string, char delimiter)
{
    int idx = string.find(delimiter);
    if (idx != BSTR_ERR) {
        return std::make_pair(psubstr(string, 0, idx), psubstr(string, idx + 1));
    } else {
        return std::make_pair(psubstr(string), psubstr());
    }
}

/// Split the string on the last occurance of the given character
///
/// The first of the returned pair will be the section of the string up to but not including `delimiter`
/// The second will be the section of the string after `delimiter` (neither will contain `delimiter`)
/// If `str` doesn't contain `delimiter`, the first will be an empty substring
inline std::pair<psubstr, psubstr>
pstr_split_last(const pstring& string, char delimiter)
{
    int idx = string.reversefind(delimiter, string.length());
    if (idx != BSTR_ERR) {
        return std::make_pair(psubstr(string, 0, idx), psubstr(string, idx + 1));
    } else {
        return std::make_pair(psubstr(), psubstr(string));
    }
}

// Encoding-aware function to replace ASCII characters in a string.
inline void
replace_ascii(pstring& string, char what, char with, const Fontinfo* fi = 0)
{
    if (what != with) {
	char* s = string.mutable_data();
	const char* e = s + string.length();
	while (s < e) {
	    int cs = file_encoding->NextCharSize(s, fi);
	    if (cs == 1 && *s == what) *s = with;
	    s += cs;
	}
    }
}


// External iterator.
class pstrIter {
    const pstring& src;
    const char* pos;
    const char* end;
    int curr;
    int csize;
    const Fontinfo* font;
public:
    inline pstrIter(const pstring& target, const Fontinfo* fi = 0);

    // Current contents as character, or -1 if none.
    inline int get() const { return curr; }

    // Current contents as encoded character.
    inline const pstring getstr() const;

    // Pointer to start of current character.
    inline const char* getptr() const;

    // Advance to next character.
    inline void next();

    // Advance by N bytes.
    inline void forward(int n);
};

pstrIter::pstrIter(const pstring& target, const Fontinfo* fi) : src(target)
{
    pos = src;
    end = pos + target.length();
    font = fi;
    next();
}

const char* pstrIter::getptr() const
{
    return pos - csize;
}

const pstring pstrIter::getstr() const
{
    return src.midstr(getptr() - (const char*) src, csize);
}

void pstrIter::forward(int n)
{
    pos += n - csize;
    next();
}

void pstrIter::next()
{
//printf("pstrIter::next - pos %08lx, end %08lx", (size_t) pos, (size_t) end);
    if (pos < end) {
	curr = file_encoding->DecodeChar(pos, csize, font);
	pos += csize;
    }
    else {
	csize = 0;
	curr = -1;
    }
//printf(" => curr = %d\n", curr);
}

pstring zentohan(const pstring&);
pstring hantozen(const pstring&);

inline pstring
file_extension(const pstring& filename) {
    int dot = filename.reversefind('.', filename.length());
    return filename.midstr(dot + 1, filename.length());
}

inline SDL_RWops*
rwops(pstring& str)
{
    return SDL_RWFromMem((void*) str.mutable_data(), str.length());
}


// Parse tags in a pstring; return new pstring with tags converted to
// bytecode.
// TODO: needs testing!
pstring parseTags(const pstring& src);

#endif
