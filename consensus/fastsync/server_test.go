package fastsync

import (
	"crypto/rand"
	"testing"

	"github.com/icon-project/goloop/common/codec"
	"github.com/icon-project/goloop/module"
	"github.com/stretchr/testify/assert"
)

const tNumLongBlocks = 1
const tNumShortBlocks = 10
const tNumBlocks = tNumShortBlocks + tNumLongBlocks

type serverTestSetUp struct {
	t  *testing.T
	bm *tBlockManager
	nm *tNetworkManager

	nm2       *tNetworkManager
	r2        *tReactor
	ph2       module.ProtocolHandler
	m         Manager
	votes     [][]byte
	rawBlocks [][]byte
}

func createABytes(l int) []byte {
	b := make([]byte, l)
	rand.Read(b)
	return b
}

func newServerTestSetUp(t *testing.T) *serverTestSetUp {
	s := &serverTestSetUp{}
	s.t = t
	s.bm = newTBlockManager()
	s.votes = make([][]byte, tNumBlocks)
	s.rawBlocks = make([][]byte, tNumBlocks)
	for i := 0; i < tNumBlocks; i++ {
		var b []byte
		if i < tNumLongBlocks {
			b = createABytes(configChunkSize * 10)
		} else {
			b = createABytes(2)
		}
		s.rawBlocks[i] = b
		s.votes[i] = b[:1]
		var prev []byte
		if i != 0 {
			prev = s.rawBlocks[i-1][:1]
		}
		s.bm.SetBlock(int64(i), newTBlock(int64(i), b[:1], prev, b[1:]))
	}
	s.nm = newTNetworkManager()
	s.nm2 = newTNetworkManager()
	s.nm.join(s.nm2)
	s.r2 = newTReactor()
	var err error
	s.ph2, err = s.nm2.RegisterReactorForStreams("fastsync", s.r2, protocols, configFastSyncPriority)
	assert.Nil(t, err)
	s.m, err = newManager(s.nm, s.bm)
	assert.Nil(t, err)
	s.m.StartServer()
	return s
}

func (s *serverTestSetUp) sendBlockRequest(ph module.ProtocolHandler, rid uint32, height int64) {
	bs := codec.MustMarshalToBytes(&BlockRequest{
		RequestID: rid,
		Height:    height,
	})
	err := s.ph2.Unicast(protoBlockRequest, bs, s.nm.id)
	assert.Nil(s.t, err)
}

func (s *serverTestSetUp) assertEqualReceiveEvent(pi module.ProtocolInfo, msg interface{}, id module.PeerID, actual interface{}) {
	b := codec.MustMarshalToBytes(msg)
	assert.Equal(s.t, tReceiveEvent{pi, b, id}, actual)
}

func TestServer_Success(t *testing.T) {
	s := newServerTestSetUp(t)
	s.sendBlockRequest(s.ph2, 0, 0)
	ev := <-s.r2.ch
	md := &BlockMetadata{0, int32(len(s.rawBlocks[0])), s.votes[1]}
	s.assertEqualReceiveEvent(protoBlockMetadata, md, s.nm.id, ev)
	recv := 0
	data := make([]byte, md.BlockLength)
	for recv < int(md.BlockLength) {
		ev = <-s.r2.ch
		ev := ev.(tReceiveEvent)
		var msg BlockData
		codec.UnmarshalFromBytes(ev.b, &msg)
		copy(data[recv:], msg.Data)
		recv += len(msg.Data)
	}
	assert.Equal(t, data, s.rawBlocks[0])
}

func TestServer_Fail(t *testing.T) {
}

func TestServer_Queue(t *testing.T) {
}

func TestServer_Cancel(t *testing.T) {
}
