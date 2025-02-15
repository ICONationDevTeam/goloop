/*
 * Copyright 2021 ICON Foundation
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

package lcimporter

import (
	"bytes"
	"math/big"

	"github.com/icon-project/goloop/common"
	"github.com/icon-project/goloop/common/codec"
	"github.com/icon-project/goloop/common/crypto"
	"github.com/icon-project/goloop/common/errors"
	"github.com/icon-project/goloop/module"
	"github.com/icon-project/goloop/service/contract"
	"github.com/icon-project/goloop/service/state"
	"github.com/icon-project/goloop/service/transaction"
)

var transactionTag = []byte{0x00}

const transactionVersion = module.TransactionVersion3

type BlockTransaction struct {
	Height        int64
	BlockID       []byte
	Result        []byte
	ValidatorHash []byte

	hash []byte
}

func (tx *BlockTransaction) PreValidate(wc state.WorldContext, update bool) error {
	return nil
}

func (tx *BlockTransaction) GetHandler(cm contract.ContractManager) (transaction.Handler, error) {
	return nil, nil
}

func (tx *BlockTransaction) Timestamp() int64 {
	return 0
}

func (tx *BlockTransaction) Nonce() *big.Int {
	return nil
}

func (tx *BlockTransaction) To() module.Address {
	return nil
}

func (tx *BlockTransaction) IsSkippable() bool {
	return false
}

func (tx *BlockTransaction) Group() module.TransactionGroup {
	return module.TransactionGroupNormal
}

func (tx *BlockTransaction) ID() []byte {
	return tx.Hash()
}

func (tx *BlockTransaction) From() module.Address {
	return nil
}

func (tx *BlockTransaction) Bytes() []byte {
	return codec.BC.MustMarshalToBytes(tx)
}

func (tx *BlockTransaction) Equal(tx2 *BlockTransaction) bool {
	return tx.Height == tx2.Height &&
		bytes.Equal(tx.BlockID, tx2.BlockID) &&
		bytes.Equal(tx.Result, tx2.Result) &&
		bytes.Equal(tx.ValidatorHash, tx2.ValidatorHash)
}

func (tx *BlockTransaction) RLPEncodeSelf(e codec.Encoder) error {
	return e.EncodeListOf(
		transactionVersion,
		transactionTag,
		tx.Height,
		tx.BlockID,
		tx.Result,
		tx.ValidatorHash,
	)
}

func (tx *BlockTransaction) RLPDecodeSelf(d codec.Decoder) error {
	var version int
	var tag []byte
	if err := d.DecodeListOf(
		&version,
		&tag,
		&tx.Height,
		&tx.BlockID,
		&tx.Result,
	); err != nil {
		return err
	}
	if version != transactionVersion ||
		!bytes.Equal(tag, transactionTag) {
		return errors.CriticalFormatError.New("InvalidVersionOrTag")
	}
	return nil
}

func (tx *BlockTransaction) Hash() []byte {
	if len(tx.hash) == 0 {
		tx.hash = crypto.SHA3Sum256(tx.Bytes())
	}
	return tx.hash
}

func (tx *BlockTransaction) Verify() error {
	return nil
}

func (tx *BlockTransaction) Version() int {
	return transactionVersion
}

func (tx *BlockTransaction) ToJSON(version module.JSONVersion) (interface{}, error) {
	return map[string]interface{}{
		"version":        transactionVersion,
		"height":         &common.HexInt64{Value: tx.Height},
		"block_id":       common.HexBytes(tx.BlockID),
		"result":         common.HexBytes(tx.Result),
		"validator_hash": common.HexBytes(tx.ValidatorHash),
	}, nil
}

func (tx *BlockTransaction) ValidateNetwork(nid int) bool {
	return true
}

type txHeader struct {
	Version int
	From    []byte
}

func checkBlockTxBytes(bs []byte) bool {
	var header txHeader
	if _, err := codec.BC.UnmarshalFromBytes(bs, &header); err != nil {
		return false
	}
	return header.Version == transactionVersion &&
		bytes.Equal(transactionTag, header.From)
}

func parseBlockTxBytes(bs []byte) (transaction.Transaction, error) {
	tx := new(BlockTransaction)
	if _, err := codec.BC.UnmarshalFromBytes(bs, tx); err != nil {
		return nil, err
	}
	return tx, nil
}

func init() {
	transaction.RegisterFactory(&transaction.Factory{
		Priority:    12,
		CheckBinary: checkBlockTxBytes,
		ParseBinary: parseBlockTxBytes,
	})
}
