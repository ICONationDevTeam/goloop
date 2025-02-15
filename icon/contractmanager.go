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

package icon

import (
	"github.com/icon-project/goloop/icon/iiss/icutils"
	"github.com/icon-project/goloop/service/state"

	"math/big"

	"github.com/icon-project/goloop/common"
	"github.com/icon-project/goloop/common/codec"
	"github.com/icon-project/goloop/common/db"
	"github.com/icon-project/goloop/common/log"
	"github.com/icon-project/goloop/module"
	"github.com/icon-project/goloop/service/contract"
)

type contractManager struct {
	contract.ContractManager
	log log.Logger
}

func (cm *contractManager) GetSystemScore(contentID string, cc contract.CallContext, from module.Address, value *big.Int) (contract.SystemScore, error) {
	if contentID == contract.CID_CHAIN {
		return newChainScore(cc, from, value)
	}
	return cm.ContractManager.GetSystemScore(contentID, cc, from, value)
}

var govAddress = common.MustNewAddressFromString("cx0000000000000000000000000000000000000001")

func (cm *contractManager) GetHandler(from, to module.Address, value *big.Int, ctype int, data []byte) (contract.ContractHandler, error) {
	if (ctype == contract.CTypeTransfer || ctype == contract.CTypeCall) && !to.IsContract() {
		return newTransferHandler(from, to, value, false, cm.log), nil
	}
	ch, err := cm.ContractManager.GetHandler(from, to, value, ctype, data)
	if err != nil {
		return nil, err
	}
	if (ctype == contract.CTypeDeploy || ctype == contract.CTypeCall) &&
		to.Equal(govAddress){
		ch = newGovernanceHandler(ch)
	}
	if (ctype == contract.CTypeCall || ctype == contract.CTypeTransfer) &&
		to.Equal(state.SystemAddress) {
		if h, ok := ch.(CallHandler); ok {
			ch = newSystemHandler(h)
		}
	}
	return ch, nil
}

func (cm *contractManager) GetCallHandler(from, to module.Address, value *big.Int, ctype int, paramObj *codec.TypedObj) (contract.ContractHandler, error) {
	switch ctype {
	case contract.CTypeTransfer:
		if !to.IsContract() {
			return newTransferHandler(from, to, value, true, cm.log), nil
		}
	case contract.CTypeCall:
		if !to.IsContract() {
			return newTransferHandler(from, to, value, true, cm.log), nil
		}
	}
	return cm.ContractManager.GetCallHandler(from, to, value, ctype, paramObj)
}

func newContractManager(plt *platform, dbase db.Database, dir string, logger log.Logger) (contract.ContractManager, error) {
	logger = icutils.NewIconLogger(logger)
	cm, err := contract.NewContractManager(dbase, dir, logger)
	if err != nil {
		return nil, err
	}
	return &contractManager{cm, logger}, nil
}
