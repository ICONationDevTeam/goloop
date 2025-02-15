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
	"github.com/icon-project/goloop/common/db"
	"github.com/icon-project/goloop/common/merkle"
	"github.com/icon-project/goloop/common/trie"
	"github.com/icon-project/goloop/common/trie/trie_manager"
	"github.com/icon-project/goloop/icon/iiss/icobject"
)

type Snapshot struct {
	store *icobject.ObjectStoreSnapshot
}

func (ss *Snapshot) Flush() error {
	if sso, ok := ss.store.ImmutableForObject.(trie.SnapshotForObject); ok {
		return sso.Flush()
	}
	return nil
}

func (ss *Snapshot) Bytes() []byte {
	return ss.store.Hash()
}

func (ss *Snapshot) Filter(prefix []byte) trie.IteratorForObject {
	return ss.store.Filter(prefix)
}

func (ss *Snapshot) GetGlobal() (Global, error) {
	key := HashKey.Append(globalKey).Build()
	o, err := ss.store.Get(key)
	if err != nil {
		return nil, err
	}
	return ToGlobal(o), nil
}

func (ss *Snapshot) GetBlockProduce(offset int) (*BlockProduce, error) {
	key := BlockProduceKey.Append(offset).Build()
	o, err := ss.store.Get(key)
	if err != nil {
		return nil, err
	}
	return ToBlockProduce(o), nil
}

func (ss *Snapshot) GetEventSize() (*EventSize, error) {
	key := HashKey.Append(eventsKey).Build()
	o, err := ss.store.Get(key)
	if err != nil {
		return nil, err
	}
	return ToEventSize(o), nil
}

func NewSnapshot(database db.Database, hash []byte) *Snapshot {
	database = icobject.AttachObjectFactory(database, NewObjectImpl)
	t := trie_manager.NewImmutableForObject(database, hash, icobject.ObjectType)
	return &Snapshot{
		store: icobject.NewObjectStoreSnapshot(t),
	}
}

func NewSnapshotWithBuilder(builder merkle.Builder, hash []byte) *Snapshot {
	database := icobject.AttachObjectFactory(builder.Database(), NewObjectImpl)
	t := trie_manager.NewImmutableForObject(database, hash, icobject.ObjectType)
	t.Resolve(builder)
	return &Snapshot{
		store: icobject.NewObjectStoreSnapshot(t),
	}
}
