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

package icreward

import (
	"github.com/icon-project/goloop/common/containerdb"
	"github.com/icon-project/goloop/common/db"
	"github.com/icon-project/goloop/common/trie/trie_manager"
	"github.com/icon-project/goloop/icon/iiss/icobject"
	"github.com/icon-project/goloop/module"
)

var (
	VotedKey      = containerdb.ToKey(containerdb.RLPBuilder, []byte{0x10})
	DelegatingKey = containerdb.ToKey(containerdb.RLPBuilder, []byte{0x20})
	BondingKey    = containerdb.ToKey(containerdb.RLPBuilder, []byte{0x30})
	IScoreKey     = containerdb.ToKey(containerdb.RLPBuilder, []byte{0x40})
)

type State struct {
	store *icobject.ObjectStoreState
}

func (s *State) GetSnapshot() *Snapshot {
	return &Snapshot{
		store: icobject.NewObjectStoreSnapshot(s.store.GetSnapshot()),
	}
}

func (s *State) Reset(ss *Snapshot) {
	s.store.Reset(ss.store.ImmutableForObject)
}

func (s *State) GetIScore(addr module.Address) (*IScore, error) {
	key := IScoreKey.Append(addr).Build()
	obj, err := s.store.Get(key)
	if err != nil {
		return nil, err
	}
	return ToIScore(obj), nil
}

func (s *State) SetIScore(addr module.Address, iScore *IScore) error {
	key := IScoreKey.Append(addr).Build()
	if iScore.IsEmpty() {
		_, err := s.store.Delete(key)
		return err
	} else {
		_, err := s.store.Set(key, icobject.New(TypeIScore, iScore))
		return err
	}
}

func (s *State) DeleteIScore(addr module.Address) error {
	key := IScoreKey.Append(addr).Build()
	_, err := s.store.Delete(key)
	return err
}

func (s *State) GetVoted(addr module.Address) (*Voted, error) {
	key := VotedKey.Append(addr).Build()
	obj, err := s.store.Get(key)
	if err != nil {
		return nil, err
	}
	return ToVoted(obj), nil
}

func (s *State) SetVoted(addr module.Address, voted *Voted) error {
	key := VotedKey.Append(addr).Build()
	_, err := s.store.Set(key, icobject.New(TypeVoted, voted))
	return err
}

func (s *State) DeleteVoted(addr module.Address) error {
	key := VotedKey.Append(addr).Build()
	_, err := s.store.Delete(key)
	return err
}

func (s *State) GetDelegating(addr module.Address) (*Delegating, error) {
	key := DelegatingKey.Append(addr).Build()
	obj, err := s.store.Get(key)
	if err != nil {
		return nil, err
	}
	return ToDelegating(obj), nil
}

func (s *State) SetDelegating(addr module.Address, delegating *Delegating) error {
	key := DelegatingKey.Append(addr).Build()
	_, err := s.store.Set(key, icobject.New(TypeDelegating, delegating))
	return err
}

func (s *State) DeleteDelegating(addr module.Address) error {
	key := DelegatingKey.Append(addr).Build()
	_, err := s.store.Delete(key)
	return err
}

func (s *State) GetBonding(addr module.Address) (*Bonding, error) {
	key := BondingKey.Append(addr).Build()
	obj, err := s.store.Get(key)
	if err != nil {
		return nil, err
	}
	return ToBonding(obj), nil
}

func (s *State) SetBonding(addr module.Address, bonding *Bonding) error {
	key := BondingKey.Append(addr).Build()
	_, err := s.store.Set(key, icobject.New(TypeBonding, bonding))
	return err
}

func (s *State) DeleteBonding(addr module.Address) error {
	key := BondingKey.Append(addr).Build()
	_, err := s.store.Delete(key)
	return err
}

func NewStateFromSnapshot(ss *Snapshot) *State {
	t := trie_manager.NewMutableFromImmutableForObject(ss.store.ImmutableForObject)
	return &State{
		store: icobject.NewObjectStoreState(t),
	}
}

func NewState(database db.Database, hash []byte) *State {
	database = icobject.AttachObjectFactory(database, NewObjectImpl)
	t := trie_manager.NewMutableForObject(database, hash, icobject.ObjectType)
	return &State{
		store: icobject.NewObjectStoreState(t),
	}
}
