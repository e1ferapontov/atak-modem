// ----------------------------------------------------------------------------
// Copyright (C) 2014
//              David Freese, W1HKJ
//
// This file is part of fldigi
//
// fldigi is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 3 of the License, or
// (at your option) any later version.
//
// fldigi is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
// ----------------------------------------------------------------------------

// Syntax: ELEM_(rsid_code, rsid_tag, fldigi_mode)
// fldigi_mode is NUM_MODES if mode is not available in fldigi,
// otherwise one of the tags defined in globals.h.
// rsid_tag is stringified and may be shown to the user.
/*
        ELEM_(263, ESCAPE, NUM_MODES)                   \
*/

//Android moved to rsid.h
//#define NUM_MODES 9999

#undef ELEM_
#define RSID_LIST                                       \
                                                        \
/* ESCAPE used to transition to 2nd RSID set */         \
        ELEM_(6, ESCAPE, NUM_MODES)                     \
                                                        \
        /* NONE must be the last element */             \
        ELEM_(0, NONE, NUM_MODES)

#define ELEM_(code_, tag_, mode_) RSID_ ## tag_ = code_,
enum { RSID_LIST };
#undef ELEM_

#define ELEM_(code_, tag_, mode_) { RSID_ ## tag_, mode_, #tag_ },
const RSIDs cRsId::rsid_ids_1[] = { RSID_LIST };
#undef ELEM_

const int cRsId::rsid_ids_size1 = sizeof(rsid_ids_1)/sizeof(*rsid_ids_1) - 1;

//======================================================================
/*        ELEM_(6, ESCAPE2, NUM_MODES)                  \ */

#define RSID_LIST2                                      \
        ELEM2_(1066, 8PSK125, MODE_8PSK125)             \
        ELEM2_(1071, 8PSK250, MODE_8PSK250)             \
        ELEM2_(1076, 8PSK500, MODE_8PSK500)             \
        ELEM2_(1047, 8PSK1000, MODE_8PSK1000)           \
                                                        \
        ELEM2_(1037, 8PSK125F, MODE_8PSK125F)           \
        ELEM2_(1038, 8PSK250F, MODE_8PSK250F)           \
													    \
		ELEM2_(1043, 8PSK500F, MODE_8PSK500F)           \
        ELEM2_(1078, 8PSK1000F, MODE_8PSK1000F)         \
        											    \
        ELEM2_(1058, 8PSK1200F, MODE_8PSK1200F)         \
                                                        \
        ELEM2_(0, NONE2, NUM_MODES)

#define ELEM2_(code_, tag_, mode_) RSID_ ## tag_ = code_,
enum { RSID_LIST2 };
#undef ELEM2_

#define ELEM2_(code_, tag_, mode_) { RSID_ ## tag_, mode_, #tag_ },
const RSIDs cRsId::rsid_ids_2[] = { RSID_LIST2 };
#undef ELEM2_

const int cRsId::rsid_ids_size2 = sizeof(rsid_ids_2)/sizeof(*rsid_ids_2) - 1;

