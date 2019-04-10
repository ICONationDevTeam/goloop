package metric

import (
	"context"
	"sync"
	"time"

	"go.opencensus.io/stats"
	"go.opencensus.io/stats/view"
	"go.opencensus.io/tag"
)

const (
	TxTypePatch  = "patch"
	TxTypeNormal = "normal"
)

var (
	msAddTx         = stats.Int64("txpool_add", "Add Transaction", stats.UnitBytes)
	msRemoveTx      = stats.Int64("txpool_remove", "Remove Transaction", stats.UnitBytes)
	msDropTx        = stats.Int64("txpool_drop", "Drop Transaction", stats.UnitBytes)
	msFinLatency    = stats.Int64("txlatency_finalize", "Finalize Transaction Latency", stats.UnitMilliseconds)
	msCommitLatency = stats.Int64("txlatency_commit", "Commit Transaction Latency", stats.UnitMilliseconds)
	mkTxType        = NewMetricKey("tx_type")
	txPoolMks       = []tag.Key{mkTxType}
)

func RegisterTransaction() {
	RegisterMetricView(msAddTx, view.Count(), txPoolMks)
	RegisterMetricView(msAddTx, view.Sum(), txPoolMks)
	RegisterMetricView(msRemoveTx, view.Count(), txPoolMks)
	RegisterMetricView(msRemoveTx, view.Sum(), txPoolMks)
	RegisterMetricView(msDropTx, view.Count(), txPoolMks)
	RegisterMetricView(msDropTx, view.Sum(), txPoolMks)
	RegisterMetricView(msFinLatency, view.LastValue(), txPoolMks)
	RegisterMetricView(msCommitLatency, view.LastValue(), txPoolMks)
}

type commitRecord struct {
	timestamp time.Time
	duration  time.Duration
}

type TxMetric struct {
	lock    sync.Mutex
	context context.Context
	commits map[string]*commitRecord
}

func (c *TxMetric) OnAddTx(n int) {
	stats.Record(c.context, msAddTx.M(int64(n)))
}

func (c *TxMetric) OnRemoveTx(n int) {
	stats.Record(c.context, msRemoveTx.M(int64(n)))
}

func (c *TxMetric) OnDropTx(n int) {
	stats.Record(c.context, msDropTx.M(int64(n)))
}

func (c *TxMetric) OnFinalize(hash []byte, ts time.Time) {
	c.lock.Lock()
	defer c.lock.Unlock()

	sHash := string(hash)
	commit, ok := c.commits[sHash]
	if !ok {
		return
	}
	delete(c.commits, sHash)
	d := ts.Sub(commit.timestamp) + commit.duration
	stats.Record(c.context, msFinLatency.M(int64(d/time.Millisecond)))
}

func (c *TxMetric) OnCommit(hash []byte, ts time.Time, d time.Duration) {
	c.lock.Lock()
	defer c.lock.Unlock()

	c.commits[string(hash)] = &commitRecord{
		timestamp: ts,
		duration:  d,
	}
	stats.Record(c.context, msCommitLatency.M(int64(d/time.Millisecond)))
}

func NewTransactionMetric(ctx context.Context, t string) *TxMetric {
	return &TxMetric{
		context: GetMetricContext(ctx, &mkTxType, t),
		commits: make(map[string]*commitRecord),
	}
}
