/*
 * Copyright 2020 ICON Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package icstage

import (
	"github.com/icon-project/goloop/common"
	"math/big"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/icon-project/goloop/common/db"
	"github.com/icon-project/goloop/icon/iiss/icobject"
)

func TestEvent_Delegation(t *testing.T) {
	database := icobject.AttachObjectFactory(db.NewMapDB(), NewObjectImpl)

	type_ := TypeEventDelegation
	version := 0
	addr1 := "hx1"
	v1 := int64(1)
	vote1 := Vote{
		Address: common.MustNewAddressFromString(addr1),
		Value:   big.NewInt(v1),
	}

	t1 := newEventVote(icobject.MakeTag(type_, version))
	t1.From = common.MustNewAddressFromString(addr1)
	t1.Votes = VoteList{&vote1}

	o1 := icobject.New(type_, t1)
	serialized := o1.Bytes()

	o2 := new(icobject.Object)
	if err := o2.Reset(database, serialized); err != nil {
		t.Errorf("Failed to get object from bytes")
		return
	}

	assert.Equal(t, serialized, o2.Bytes())
	assert.Equal(t, type_, o2.Tag().Type())
	assert.Equal(t, version, o2.Tag().Version())

	t2 := ToEventVote(o2)
	assert.Equal(t, true, t1.Equal(t2))
	assert.Equal(t, true, t1.From.Equal(t2.From))
	assert.Equal(t, true, t1.Votes.Equal(t2.Votes))
}

func TestEvent_Bond(t *testing.T) {
	database := icobject.AttachObjectFactory(db.NewMapDB(), NewObjectImpl)

	type_ := TypeEventBond
	version := 0
	addr1 := "hx1"
	v1 := int64(1)
	vote1 := Vote{
		Address: common.MustNewAddressFromString(addr1),
		Value:   big.NewInt(v1),
	}

	t1 := newEventVote(icobject.MakeTag(type_, version))
	t1.From = common.MustNewAddressFromString(addr1)
	t1.Votes = VoteList{&vote1}

	o1 := icobject.New(type_, t1)
	serialized := o1.Bytes()

	o2 := new(icobject.Object)
	if err := o2.Reset(database, serialized); err != nil {
		t.Errorf("Failed to get object from bytes")
		return
	}

	assert.Equal(t, serialized, o2.Bytes())
	assert.Equal(t, type_, o2.Tag().Type())
	assert.Equal(t, version, o2.Tag().Version())

	t2 := ToEventVote(o2)
	assert.Equal(t, true, t1.Equal(t2))
	assert.Equal(t, true, t1.From.Equal(t2.From))
	assert.Equal(t, true, t1.Votes.Equal(t2.Votes))
}

func TestEvent_Enable(t *testing.T) {
	database := icobject.AttachObjectFactory(db.NewMapDB(), NewObjectImpl)

	type_ := TypeEventEnable
	version := 0
	addr1 := "hx1"
	flag := EfDisablePermanent

	t1 := newEventEnable(icobject.MakeTag(type_, version))
	t1.Target = common.MustNewAddressFromString(addr1)
	t1.Flag = flag

	o1 := icobject.New(type_, t1)
	serialized := o1.Bytes()

	o2 := new(icobject.Object)
	if err := o2.Reset(database, serialized); err != nil {
		t.Errorf("Failed to get object from bytes")
		return
	}

	assert.Equal(t, serialized, o2.Bytes())
	assert.Equal(t, type_, o2.Tag().Type())
	assert.Equal(t, version, o2.Tag().Version())

	t2 := ToEventEnable(o2)
	assert.Equal(t, true, t1.Equal(t2))
	assert.Equal(t, true, t1.Target.Equal(t2.Target))
	assert.Equal(t, t1.Flag, t2.Flag)
}

func TestVoteList_Update(t *testing.T) {
	addr1, _ := common.NewAddressFromString("hx1")
	addr2, _ := common.NewAddressFromString("hx2")
	addr3, _ := common.NewAddressFromString("hx3")
	addr4, _ := common.NewAddressFromString("hx4")
	vote1 := Vote{addr1, big.NewInt(1)}
	vote1Neg := Vote{addr1, big.NewInt(-1)}
	vote2 := Vote{addr2, big.NewInt(2)}
	vote2Neg := Vote{addr2, big.NewInt(-2)}
	vote3 := Vote{addr3, big.NewInt(3)}
	vote3Neg := Vote{addr3, big.NewInt(-3)}
	vote4 := Vote{addr4, big.NewInt(4)}

	voteList := VoteList{vote1.Clone(), vote2.Clone(), vote3.Clone()}

	tests := []struct {
		name  string
		input VoteList
		want  VoteList
	}{
		{
			"Delete first item",
			VoteList{&vote1Neg},
			VoteList{&vote2, &vote3},
		},
		{
			"Delete second",
			VoteList{&vote2Neg},
			VoteList{&vote1, &vote3},
		},
		{
			"Delete last",
			VoteList{&vote3Neg},
			VoteList{&vote1, &vote2},
		},
		{
			"Delete second and last",
			VoteList{&vote2Neg, &vote3Neg},
			VoteList{&vote1},
		},
		{
			"Delete all",
			VoteList{&vote1Neg, &vote2Neg, &vote3Neg},
			VoteList{},
		},
		{
			"Update value",
			VoteList{&vote1, &vote2, &vote3},
			VoteList{
				&Vote{addr1, big.NewInt(2)},
				&Vote{addr2, big.NewInt(4)},
				&Vote{addr3, big.NewInt(6)},
			},
		},
		{
			"Delete first and update second",
			VoteList{&vote1Neg, &vote2},
			VoteList{&Vote{addr2, big.NewInt(4)}, &vote3},
		},
		{
			"Add new vote",
			VoteList{&vote1, &vote2, &vote4},
			VoteList{
				&Vote{addr1, big.NewInt(2)},
				&Vote{addr2, big.NewInt(4)},
				&vote3,
				&vote4,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			vl := voteList.Clone()
			vl.Update(tt.input)
			assert.True(t, vl.Equal(tt.want))
		})
	}
}
