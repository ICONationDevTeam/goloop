// Code generated by go generate; DO NOT EDIT.
package test

import (
	"math/big"

	"github.com/icon-project/goloop/module"
)

type ServiceManagerBase struct{}

func (_r *ServiceManagerBase) ProposeTransition(parent module.Transition, bi module.BlockInfo) (module.Transition, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) CreateInitialTransition(result []byte, nextValidators module.ValidatorList) (module.Transition, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) CreateTransition(parent module.Transition, txs module.TransactionList, bi module.BlockInfo) (module.Transition, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) GetPatches(parent module.Transition) module.TransactionList {
	panic("not implemented")
}

func (_r *ServiceManagerBase) PatchTransition(transition module.Transition, patches module.TransactionList) module.Transition {
	panic("not implemented")
}

func (_r *ServiceManagerBase) Start() {
	panic("not implemented")
}

func (_r *ServiceManagerBase) Term() {
	panic("not implemented")
}

func (_r *ServiceManagerBase) Finalize(transition module.Transition, opt int) error {
	panic("not implemented")
}

func (_r *ServiceManagerBase) TransactionFromBytes(b []byte, blockVersion int) (module.Transaction, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) GenesisTransactionFromBytes(b []byte, blockVersion int) (module.Transaction, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) TransactionListFromHash(hash []byte) module.TransactionList {
	panic("not implemented")
}

func (_r *ServiceManagerBase) TransactionListFromSlice(txs []module.Transaction, version int) module.TransactionList {
	panic("not implemented")
}

func (_r *ServiceManagerBase) ReceiptListFromResult(result []byte, g module.TransactionGroup) module.ReceiptList {
	panic("not implemented")
}

func (_r *ServiceManagerBase) SendTransaction(tx interface{}) ([]byte, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) Call(result []byte, vl module.ValidatorList, js []byte, bi module.BlockInfo) (interface{}, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) ValidatorListFromHash(hash []byte) module.ValidatorList {
	panic("not implemented")
}

func (_r *ServiceManagerBase) GetBalance(result []byte, addr module.Address) (*big.Int, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) GetTotalSupply(result []byte) (*big.Int, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) GetNetworkID(result []byte) (int64, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) GetAPIInfo(result []byte, addr module.Address) (module.APIInfo, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) GetMembers(result []byte) (module.MemberList, error) {
	panic("not implemented")
}

func (_r *ServiceManagerBase) HasTransaction(id []byte) bool {
	panic("not implemented")
}
