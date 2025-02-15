package iiss

import (
	"container/list"
	"fmt"
	"github.com/icon-project/goloop/common/errors"
	"github.com/icon-project/goloop/common/log"
	"github.com/icon-project/goloop/icon/iiss/icstate"
	"github.com/icon-project/goloop/icon/iiss/icutils"
	"github.com/icon-project/goloop/module"
	"github.com/icon-project/goloop/service/state"
)

type ValidatorItemIterator interface {
	Has() bool
	Next() error
	Get() (*ValidatorItem, error)
}

type ValidatorItem struct {
	v     module.Validator
	added bool
}

func (vi *ValidatorItem) Address() module.Address {
	return vi.v.Address()
}

func (vi *ValidatorItem) IsAdded() bool {
	return vi.added
}

func (vi *ValidatorItem) ResetFlags() {
	vi.added = false
}

func validatorFromAddress(a module.Address, added bool) (*ValidatorItem, error) {
	if a == nil {
		return nil, errors.ErrIllegalArgument
	}

	if a.IsContract() {
		return nil, errors.ErrIllegalArgument
	}
	v, err := state.ValidatorFromAddress(a)
	if err != nil {
		return nil, err
	}
	return &ValidatorItem{
		added: added,
		v:     v,
	}, nil
}

type ValidatorManager struct {
	icutils.Immutable

	updated bool
	pssIdx  int // The index of next validator candidate in term
	vlist   *list.List
	vmap    map[string]*list.Element
}

func (vm *ValidatorManager) Hash() []byte {
	return nil
}

func (vm *ValidatorManager) Bytes() []byte {
	return nil
}

func (vm *ValidatorManager) Flush() error {
	return nil
}

func (vm *ValidatorManager) IndexOf(address module.Address) int {
	i := 0
	for e := vm.vlist.Front(); e != nil; e = e.Next() {
		vi := e.Value.(*ValidatorItem)
		if address.Equal(vi.Address()) {
			return i
		}
		i++
	}
	return -1
}

func (vm *ValidatorManager) Len() int {
	return vm.vlist.Len()
}

func (vm *ValidatorManager) PRepSnapshotIndex() int {
	return vm.pssIdx
}

func (vm *ValidatorManager) SetPRepSnapshotIndex(idx int) {
	vm.pssIdx = idx
}

func (vm *ValidatorManager) Get(idx int) (*ValidatorItem, bool) {
	size := vm.Len()
	if idx < 0 || idx > size {
		return nil, false
	}

	e := vm.vlist.Front()
	for i := 0; i < idx; i++ {
		e = e.Next()
	}
	return e.Value.(*ValidatorItem), true
}

func (vm *ValidatorManager) IsUpdated() bool {
	return vm.updated
}

func (vm *ValidatorManager) SetUpdated(on bool) {
	vm.updated = on
}

func (vm *ValidatorManager) ResetUpdated() {
	vm.updated = false
}

func (vm *ValidatorManager) Add(node module.Address) error {
	return vm.add(node, true)
}

func (vm *ValidatorManager) add(node module.Address, added bool) error {
	if err := vm.checkWritable(); err != nil {
		return err
	}
	if node == nil {
		return errors.Errorf("Node address is nil")
	}

	key := icutils.ToKey(node)
	if _, ok := vm.vmap[key]; ok {
		return nil
	}

	v, err := validatorFromAddress(node, added)
	if err != nil {
		return err
	}

	e := vm.vlist.PushBack(v)
	vm.vmap[key] = e
	if added {
		vm.updated = true
	}
	return nil
}

// Replace is used for node address change
func (vm *ValidatorManager) Replace(oldNode, newNode module.Address) error {
	key := icutils.ToKey(oldNode)
	e, ok := vm.vmap[key]
	if !ok {
		return errors.Errorf("Node not found: %s", oldNode)
	}
	_, ok = vm.vmap[icutils.ToKey(newNode)]
	if ok {
		return errors.Errorf("Node already in use: %s", newNode)
	}

	v, err := validatorFromAddress(newNode, false)
	if err != nil {
		return err
	}

	e.Value = v
	vm.updated = true
	return nil
}

func (vm *ValidatorManager) Remove(node module.Address) error {
	if err := vm.checkWritable(); err != nil {
		return err
	}

	key := icutils.ToKey(node)
	e, ok := vm.vmap[key]
	if !ok {
		return errors.Errorf("Node not found: %s", node)
	}

	vm.vlist.Remove(e)
	delete(vm.vmap, key)
	vm.updated = true
	return nil
}

func (vm *ValidatorManager) Clear() error {
	if vm.Len() > 0 {
		vm.pssIdx = 0
		vm.vlist = vm.vlist.Init()
		vm.vmap = make(map[string]*list.Element)
		vm.updated = false
	}
	return nil
}

func (vm *ValidatorManager) GetValidators() ([]module.Validator, error) {
	size := vm.Len()
	vs := make([]module.Validator, size, size)
	e := vm.vlist.Front()
	for i := 0; i < size; i++ {
		vs[i] = e.Value.(*ValidatorItem).v
		e = e.Next()
	}
	return vs, nil
}

func (vm *ValidatorManager) Init(pm *PRepManager, term *icstate.Term) error {
	if vm.Len() > 0 {
		return errors.Errorf("ValidatorManager is not empty")
	}
	return vm.load(pm, term, false)
}

func (vm *ValidatorManager) Load(pm *PRepManager, term *icstate.Term) error {
	return vm.load(pm, term, true)
}

func (vm *ValidatorManager) load(pm *PRepManager, term *icstate.Term, added bool) error {
	if err := vm.checkWritable(); err != nil {
		return err
	}

	mainPReps := pm.GetPRepSize(icstate.Main)
	vLen := vm.Len()

	if vLen == mainPReps {
		return nil
	}
	if vLen > mainPReps {
		return errors.Errorf("Invalid validators: validators(%d) > mainPReps(%d)", vLen, mainPReps)
	}

	pssCount := term.GetPRepSnapshotCount()
	for i := vm.pssIdx; i < pssCount; i++ {
		pss := term.GetPRepSnapshotByIndex(i)
		prep := pm.GetPRepByOwner(pss.Owner())
		if prep == nil {
			log.Infof("PRep not found in ValidatorManager: %s", pss.Owner())
			continue
		}
		if prep.Grade() == icstate.Main {
			if err := vm.add(prep.GetNode(), added); err != nil {
				return err
			}
			if vm.Len() == mainPReps {
				vm.pssIdx = i + 1
				break
			}
		}
	}
	if vm.Len() != mainPReps {
		return errors.Errorf("Invalid validators: validators(%d) != mainPReps(%d)", vLen, mainPReps)
	}
	return nil
}

func (vm *ValidatorManager) checkWritable() error {
	if vm.IsReadonly() {
		return errors.Errorf("Writing is not allowed: %v", vm)
	}
	return nil
}

func (vm *ValidatorManager) String() string {
	return fmt.Sprintf("ValidatorManager: size=%d", vm.Len())
}

type viIterator struct {
	e *list.Element
}

func (vii *viIterator) Has() bool {
	return vii.e != nil
}

func (vii *viIterator) Next() error {
	vii.e = vii.e.Next()
	if vii.e == nil {
		return errors.Errorf("Stop iteration")
	}
	return nil
}

func (vii *viIterator) Get() (*ValidatorItem, error) {
	if vii.e == nil {
		return nil, errors.Errorf("Invalid value")
	}
	return vii.e.Value.(*ValidatorItem), nil
}

func (vm *ValidatorManager) Iterator() *viIterator {
	return &viIterator{
		e: vm.vlist.Front(),
	}
}

func NewValidatorManager() *ValidatorManager {
	return &ValidatorManager{
		vlist: list.New(),
		vmap:  make(map[string]*list.Element),
	}
}
