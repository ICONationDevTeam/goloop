/*
 * Copyright 2019 ICON Foundation
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

package foundation.icon.test.cases;

import example.IRC2BasicToken;
import example.IRC3BasicToken;
import example.token.IRC2;
import example.token.IRC2Basic;
import example.token.IRC3;
import example.token.IRC3Basic;
import example.util.EnumerableMap;
import example.util.IntSet;
import example.util.IntToAddressMap;
import foundation.icon.ee.util.Crypto;
import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.SignedTransaction;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Base64;
import foundation.icon.icx.data.Block;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.ConfirmedTransaction;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcError;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.common.Constants;
import foundation.icon.test.common.Env;
import foundation.icon.test.common.TestBase;
import foundation.icon.test.common.TransactionHandler;
import foundation.icon.test.score.IRC3TokenScore;
import foundation.icon.test.score.SampleTokenScore;
import foundation.icon.test.score.Score;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import testcases.APITest;
import testcases.DeployScore;
import testcases.HelloWorld;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import static foundation.icon.test.common.Env.LOG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(Constants.TAG_JAVA_SCORE)
class JavaScoreTest extends TestBase {
    private static final Address ZERO_ADDRESS = new Address("hx0000000000000000000000000000000000000000");
    private static IconService iconService;
    private static TransactionHandler txHandler;
    private static SecureRandom secureRandom;
    private static KeyWallet[] wallets;
    private static KeyWallet ownerWallet, caller;
    private static Score testScore;

    @BeforeAll
    static void init() throws Exception {
        Env.Node node = Env.nodes[0];
        Env.Channel channel = node.channels[0];
        Env.Chain chain = channel.chain;
        iconService = new IconService(new HttpProvider(channel.getAPIUrl(Env.testApiVer)));
        txHandler = new TransactionHandler(iconService, chain);
        secureRandom = new SecureRandom();

        // init wallets
        wallets = new KeyWallet[2];
        BigInteger amount = ICX.multiply(BigInteger.valueOf(100));
        for (int i = 0; i < wallets.length; i++) {
            wallets[i] = KeyWallet.create();
            txHandler.transfer(wallets[i].getAddress(), amount);
        }
        for (KeyWallet wallet : wallets) {
            ensureIcxBalance(txHandler, wallet.getAddress(), BigInteger.ZERO, amount);
        }
        ownerWallet = wallets[0];
        caller = wallets[1];
    }

    @AfterAll
    static void shutdown() throws Exception {
        for (KeyWallet wallet : wallets) {
            txHandler.refundAll(wallet);
        }
    }

    @Test
    public void testSampleToken() throws Exception {
        // 1. deploy
        BigInteger decimals = BigInteger.valueOf(18);
        BigInteger initialSupply = BigInteger.valueOf(1000);
        SampleTokenScore tokenScore = SampleTokenScore.mustDeploy(txHandler, ownerWallet,
                decimals, initialSupply, Constants.CONTENT_TYPE_JAVA);
        startTokenTest(tokenScore, decimals, initialSupply);
    }

    @Test
    public void testIRC2Token() throws Exception {
        // 1. deploy
        BigInteger decimals = BigInteger.valueOf(18);
        BigInteger initialSupply = BigInteger.valueOf(1000);
        SampleTokenScore tokenScore = SampleTokenScore.mustDeploy(txHandler, ownerWallet,
                decimals, initialSupply, new Class<?>[]{IRC2BasicToken.class, IRC2Basic.class, IRC2.class});
        startTokenTest(tokenScore, decimals, initialSupply);
    }

    private void startTokenTest(SampleTokenScore tokenScore, BigInteger decimals, BigInteger initialSupply)
            throws Exception {
        KeyWallet calleeWallet = KeyWallet.create();

        // 2. balanceOf
        LOG.infoEntering("balanceOf", "owner (initial)");
        BigInteger oneToken = BigInteger.TEN.pow(decimals.intValue());
        BigInteger totalSupply = oneToken.multiply(initialSupply);
        BigInteger bal = tokenScore.balanceOf(ownerWallet.getAddress());
        LOG.info("expected (" + totalSupply + "), got (" + bal + ")");
        assertEquals(totalSupply, bal);
        LOG.infoExiting();

        // 3. transfer #1
        LOG.infoEntering("transfer", "#1");
        TransactionResult result = tokenScore.transfer(ownerWallet, calleeWallet.getAddress(), oneToken);
        tokenScore.ensureTransfer(result, ownerWallet.getAddress(), calleeWallet.getAddress(), oneToken, null);
        LOG.infoExiting();

        // 3.1 transfer #2
        LOG.infoEntering("transfer", "#2");
        BigInteger two = oneToken.add(oneToken);
        byte[] data = "Hello".getBytes();
        result = tokenScore.transfer(ownerWallet, calleeWallet.getAddress(), two, data);
        assertSuccess(result);
        tokenScore.ensureTransfer(result, ownerWallet.getAddress(), calleeWallet.getAddress(), two, data);
        LOG.infoExiting();

        // 4. check balance of callee
        LOG.infoEntering("balanceOf", "callee");
        BigInteger expected = oneToken.add(two);
        bal = tokenScore.balanceOf(calleeWallet.getAddress());
        LOG.info("expected (" + expected + "), got (" + bal + ")");
        assertEquals(expected, bal);
        LOG.infoExiting();

        // 5. check balance of owner
        LOG.infoEntering("balanceOf", "owner");
        expected = totalSupply.subtract(expected);
        bal = tokenScore.balanceOf(ownerWallet.getAddress());
        LOG.info("expected (" + expected + "), got (" + bal + ")");
        assertEquals(expected, bal);
        LOG.infoExiting();
    }

    private Score deployAPITest() throws Exception {
        if (testScore == null) {
            LOG.infoEntering("deploy", "apiTest");
            testScore = txHandler.deploy(ownerWallet, APITest.class, null);
            LOG.info("scoreAddr = " + testScore.getAddress());
            LOG.infoExiting();
        }
        return testScore;
    }

    @Test
    public void testIRC3Token() throws Exception {
        // 1. deploy
        IRC3TokenScore tokenScore = IRC3TokenScore.mustDeploy(
                txHandler, ownerWallet, new Class<?>[]{
                        IRC3BasicToken.class, IRC3Basic.class, IRC3.class,
                        EnumerableMap.class, IntSet.class, IntToAddressMap.class});

        // 2. initial check
        LOG.infoEntering("initial check");
        assertEquals(BigInteger.ZERO, tokenScore.balanceOf(ownerWallet.getAddress()));
        assertEquals(BigInteger.ZERO, tokenScore.balanceOf(caller.getAddress()));
        assertEquals(BigInteger.ZERO, tokenScore.totalSupply());
        LOG.infoExiting();

        // 3. mint some tokens
        LOG.infoEntering("mint some tokens");
        BigInteger[] tokenId = new BigInteger[] {
                new BigInteger(getRandomBytes(8)),
                new BigInteger(getRandomBytes(8)),
                new BigInteger(getRandomBytes(8)),
                new BigInteger(getRandomBytes(8)),
        };
        Bytes[] ids = new Bytes[tokenId.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = tokenScore.mint(ownerWallet, tokenId[i]);
        }
        for (Bytes id : ids) {
            assertSuccess(txHandler.getResult(id));
        }
        assertEquals(BigInteger.valueOf(tokenId.length), tokenScore.balanceOf(ownerWallet.getAddress()));
        assertEquals(BigInteger.valueOf(tokenId.length), tokenScore.totalSupply());
        showTokenStatus(tokenScore);
        LOG.infoExiting();

        // 4. transfer and check
        LOG.infoEntering("transfer and check");
        BigInteger token = tokenId[0];
        assertEquals(ownerWallet.getAddress(), tokenScore.ownerOf(token));
        ids[0] = tokenScore.transfer(ownerWallet, caller.getAddress(), token);
        assertSuccess(txHandler.getResult(ids[0]));
        assertEquals(caller.getAddress(), tokenScore.ownerOf(token));
        assertEquals(BigInteger.ONE, tokenScore.balanceOf(caller.getAddress()));
        assertEquals(BigInteger.valueOf(tokenId.length-1), tokenScore.balanceOf(ownerWallet.getAddress()));
        assertEquals(BigInteger.valueOf(tokenId.length), tokenScore.totalSupply());
        assertEquals(token, tokenScore.tokenOfOwnerByIndex(caller.getAddress(), 0));
        assertEquals(tokenId[tokenId.length-1], tokenScore.tokenOfOwnerByIndex(ownerWallet.getAddress(), 0));
        showTokenStatus(tokenScore);
        LOG.infoExiting();

        // 5. approve and check
        LOG.infoEntering("approve and check");
        token = tokenId[1];
        assertEquals(ZERO_ADDRESS, tokenScore.getApproved(token));
        ids[1] = tokenScore.approve(ownerWallet, caller.getAddress(), token);
        assertSuccess(txHandler.getResult(ids[1]));
        assertEquals(caller.getAddress(), tokenScore.getApproved(token));
        showTokenStatus(tokenScore);

        assertEquals(ownerWallet.getAddress(), tokenScore.ownerOf(token));
        ids[2] = tokenScore.transferFrom(caller, ownerWallet.getAddress(), caller.getAddress(), token);
        assertSuccess(txHandler.getResult(ids[2]));
        assertEquals(ZERO_ADDRESS, tokenScore.getApproved(token));
        assertEquals(caller.getAddress(), tokenScore.ownerOf(token));
        assertEquals(BigInteger.TWO, tokenScore.balanceOf(caller.getAddress()));
        assertEquals(BigInteger.valueOf(tokenId.length-2), tokenScore.balanceOf(ownerWallet.getAddress()));
        assertEquals(BigInteger.valueOf(tokenId.length), tokenScore.totalSupply());
        assertEquals(token, tokenScore.tokenOfOwnerByIndex(caller.getAddress(), 1));
        assertEquals(tokenId[tokenId.length-2], tokenScore.tokenOfOwnerByIndex(ownerWallet.getAddress(), 1));
        showTokenStatus(tokenScore);
        LOG.infoExiting();

        // 6. burn and check
        LOG.infoEntering("burn and check");
        var balance = tokenScore.balanceOf(ownerWallet.getAddress());
        token = tokenScore.tokenOfOwnerByIndex(ownerWallet.getAddress(), 0);
        ids[0] = tokenScore.burn(ownerWallet, token);
        assertSuccess(txHandler.getResult(ids[0]));
        assertEquals(balance.subtract(BigInteger.ONE), tokenScore.balanceOf(ownerWallet.getAddress()));
        assertEquals(BigInteger.valueOf(tokenId.length-1), tokenScore.totalSupply());
        showTokenStatus(tokenScore);
        LOG.infoExiting();

        // 7. negative tests
        LOG.infoEntering("negative tests");
        final var nonExistToken = token; // burned token
        assertThrows(RpcError.class, () -> tokenScore.ownerOf(nonExistToken));
        assertFailure(txHandler.getResult(
                tokenScore.transferFrom(caller, ownerWallet.getAddress(), caller.getAddress(), tokenId[2])));
        LOG.infoExiting();
    }

    private void showTokenStatus(IRC3TokenScore tokenScore) throws Exception {
        var totalSupply = tokenScore.totalSupply();
        System.out.println(">>> totalSupply = " + totalSupply);
        for (int i = 0; i < totalSupply.intValue(); i++) {
            var token = tokenScore.tokenByIndex(i);
            var owner = tokenScore.ownerOf(token);
            var approved = tokenScore.getApproved(token);
            System.out.printf("   [%s](%s)<%s>\n", token, owner,
                    approved.equals(ZERO_ADDRESS) ? "null" : approved);
        }
        var ownerBalance = tokenScore.balanceOf(ownerWallet.getAddress());
        System.out.println("   == balanceOf owner: " + ownerBalance);
        for (int i = 0; i < ownerBalance.intValue(); i++) {
            var token = tokenScore.tokenOfOwnerByIndex(ownerWallet.getAddress(), i);
            System.out.printf("     -- %d: [%s]\n", i, token);
        }
        var callerBalance = tokenScore.balanceOf(caller.getAddress());
        System.out.println("   == balanceOf caller: " + callerBalance);
        for (int i = 0; i < callerBalance.intValue(); i++) {
            var token = tokenScore.tokenOfOwnerByIndex(caller.getAddress(), i);
            System.out.printf("     -- %d: [%s]\n", i, token);
        }
    }

    private byte[] getRandomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        bytes[0] = 0; // make positive
        return bytes;
    }

    static class TestCase {
        private final String method;
        private final RpcObject params;
        private final BigInteger expectedStatus;

        TestCase(String method, RpcObject params, BigInteger expectedStatus) {
            this.method = method;
            this.params = params;
            this.expectedStatus = expectedStatus;
        }
    }

    @Test
    public void testAPIForAddress() throws Exception {
        Score apiScore = deployAPITest();
        TransactionResult tr;

        LOG.infoEntering("invoke");
        TestCase[] testCases = {
                new TestCase("getAddress", new RpcObject.Builder()
                        .put("addr", new RpcValue(apiScore.getAddress())).build(),
                        Constants.STATUS_SUCCESS),
                new TestCase("getAddress", new RpcObject.Builder()
                        .put("addr", new RpcValue(caller.getAddress())).build(),
                        Constants.STATUS_FAILURE),
                new TestCase("getCaller", new RpcObject.Builder()
                        .put("caller", new RpcValue(caller.getAddress())).build(),
                        Constants.STATUS_SUCCESS),
                new TestCase("getCaller", new RpcObject.Builder()
                        .put("caller", new RpcValue(ownerWallet.getAddress())).build(),
                        Constants.STATUS_FAILURE),
                new TestCase("getOrigin", new RpcObject.Builder()
                        .put("origin", new RpcValue(caller.getAddress())).build(),
                        Constants.STATUS_SUCCESS),
                new TestCase("getOrigin", new RpcObject.Builder()
                        .put("origin", new RpcValue(ownerWallet.getAddress())).build(),
                        Constants.STATUS_FAILURE),
                new TestCase("getOwner", new RpcObject.Builder()
                        .put("owner", new RpcValue(ownerWallet.getAddress())).build(),
                        Constants.STATUS_SUCCESS),
                new TestCase("getOwner", new RpcObject.Builder()
                        .put("owner", new RpcValue(caller.getAddress())).build(),
                        Constants.STATUS_FAILURE),
        };

        Bytes[] ids = new Bytes[testCases.length];
        int cnt = 0;
        for (TestCase tc : testCases) {
            LOG.info(tc.method);
            ids[cnt++] = apiScore.invoke(caller, tc.method, tc.params);
        }
        for (int i = 0; i < cnt; i++) {
            tr = txHandler.getResult(ids[i]);
            assertStatus(testCases[i].expectedStatus, tr);
            if (tr.getFailure() != null) {
                LOG.info("Expected " + tr.getFailure());
            }
        }
        LOG.infoExiting();

        LOG.infoEntering("getAddress", "query");
        RpcItem result = apiScore.call("getAddressQuery", null);
        LOG.info("expected (" + apiScore.getAddress() + "), got (" + result.asAddress() + ")");
        assertEquals(apiScore.getAddress(), result.asAddress());
        LOG.infoExiting();

        LOG.infoEntering("getCaller", "query");
        result = apiScore.call("getCallerQuery", null);
        LOG.info("expected (" + "null" + "), got (" + result + ")");
        assertNull(result);
        LOG.infoExiting();

        LOG.infoEntering("getOrigin", "query");
        result = apiScore.call("getOriginQuery", null);
        LOG.info("expected (" + "null" + "), got (" + result + ")");
        assertNull(result);
        LOG.infoExiting();

        LOG.infoEntering("getOwner", "query");
        result = apiScore.call("getOwnerQuery", null);
        LOG.info("expected (" + ownerWallet.getAddress() + "), got (" + result.asAddress() + ")");
        assertEquals(ownerWallet.getAddress(), result.asAddress());
        LOG.infoExiting();
    }

    @Test
    public void testAPIForBlock() throws Exception {
        Score apiScore = deployAPITest();
        TransactionResult tr;
        RpcItem result = RpcValue.NULL;

        // getBlockHeight
        LOG.infoEntering("getBlockHeight", "invoke");
        tr = apiScore.invokeAndWaitResult(caller, "getBlockHeight", null);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + tr.getBlockHeight() + "), got (" + result.asInteger() + ")");
        assertEquals(0, tr.getBlockHeight().compareTo(result.asInteger()));
        LOG.infoExiting();

        LOG.infoEntering("getBlockHeight", "query");
        Block block = iconService.getLastBlock().execute();
        result = apiScore.call("getBlockHeightQuery", null);
        LOG.info("expected (" + block.getHeight() + "), got (" + result.asInteger() + ")");
        assertTrue(block.getHeight().compareTo(result.asInteger()) <= 0);
        LOG.infoExiting();

        // getBlockTimestamp
        LOG.infoEntering("getBlockTimestamp", "invoke");
        tr = apiScore.invokeAndWaitResult(caller, "getBlockTimestamp", null);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        block = iconService.getBlock(tr.getBlockHeight()).execute();
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + block.getTimestamp() + "), got (" + result.asInteger() + ")");
        assertEquals(0, block.getTimestamp().compareTo(result.asInteger()));
        LOG.infoExiting();

        LOG.infoEntering("getBlockTimestamp", "query");
        block = iconService.getLastBlock().execute();
        result = apiScore.call("getBlockTimestampQuery", null);
        LOG.info("expected (" + block.getTimestamp() + "), got (" + result.asInteger() + ")");
        assertTrue(block.getTimestamp().compareTo(result.asInteger()) <= 0);
        LOG.infoExiting();
    }

    @Test
    public void testAPIForTransaction() throws Exception {
        Score apiScore = deployAPITest();
        TransactionResult tr;
        RpcItem result = RpcValue.NULL;

        // getTransactionHash
        LOG.infoEntering("getTransactionHash", "invoke");
        tr = apiScore.invokeAndWaitResult(caller, "getTransactionHash", null);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + tr.getTxHash() + "), got (" + result.asString() + ")");
        assertEquals(tr.getTxHash().toString(), result.asString());
        LOG.infoExiting();

        LOG.infoEntering("getTransactionHash", "query");
        result = apiScore.call("getTransactionHashQuery", null);
        LOG.info("expected (" + "null" + "), got (" + result + ")");
        assertNull(result);
        LOG.infoExiting();

        // getTransactionIndex
        LOG.infoEntering("getTransactionIndex", "invoke");
        Bytes[] ids = new Bytes[5];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = apiScore.invoke(caller, "getTransactionIndex", null);
        }
        for (Bytes id : ids) {
            tr = apiScore.getResult(id);
            assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
            for (TransactionResult.EventLog e : tr.getEventLogs()) {
                RpcItem data = e.getData().get(0);
                LOG.info("expected (" + tr.getTxIndex() + "), got (" + data.asInteger() + ")");
                assertEquals(tr.getTxIndex(), data.asInteger());
            }
        }
        LOG.infoExiting();

        LOG.infoEntering("getTransactionIndex", "query");
        result = apiScore.call("getTransactionIndexQuery", null);
        LOG.info("expected (" + "0" + "), got (" + result.asInteger() + ")");
        assertEquals(BigInteger.ZERO, result.asInteger());
        LOG.infoExiting();

        // getTransactionTimestamp
        LOG.infoEntering("getTransactionTimestamp", "invoke");
        BigInteger steps = BigInteger.valueOf(200000);
        // Add arbitrary milliseconds precision for testing
        BigInteger timestamp = BigInteger.valueOf((System.currentTimeMillis() * 1000L) - secureRandom.nextInt(100));
        Bytes tid = apiScore.invoke(caller, "getTransactionTimestamp", null, null, steps, timestamp, null);
        tr = apiScore.getResult(tid);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        ConfirmedTransaction ctx = iconService.getTransaction(tr.getTxHash()).execute();
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + ctx.getTimestamp() + "), got (" + result.asInteger() + ")");
        assertEquals(0, ctx.getTimestamp().compareTo(result.asInteger()));
        LOG.infoExiting();

        LOG.infoEntering("getTransactionTimestamp", "query");
        result = apiScore.call("getTransactionTimestampQuery", null);
        LOG.info("expected (" + "0" + "), got (" + result.asInteger() + ")");
        assertEquals(BigInteger.ZERO, result.asInteger());
        LOG.infoExiting();

        // getTransactionNonce
        LOG.infoEntering("getTransactionNonce", "invoke");
        BigInteger nonce = BigInteger.valueOf(0x12345);
        tid = apiScore.invoke(caller, "getTransactionNonce", null, null, steps, null, nonce);
        tr = apiScore.getResult(tid);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + nonce + "), got (" + result.asInteger() + ")");
        assertEquals(nonce, result.asInteger());
        LOG.infoExiting();

        LOG.infoEntering("getTransactionNonce", "query");
        result = apiScore.call("getTransactionNonceQuery", null);
        LOG.info("expected (" + "0" + "), got (" + result.asInteger() + ")");
        assertEquals(BigInteger.ZERO, result.asInteger());
        LOG.infoExiting();
    }

    @Test
    public void testAPIForCoin() throws Exception {
        Score apiScore = deployAPITest();
        TransactionResult tr;
        RpcItem result = RpcValue.NULL;

        // getValue
        LOG.infoEntering("getValue", "invoke");
        BigInteger ownerBalance = txHandler.getBalance(ownerWallet.getAddress());
        tr = apiScore.invokeAndWaitResult(ownerWallet, "getValue", null, ICX, Constants.DEFAULT_STEPS);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + ICX + "), got (" + result.asInteger() + ")");
        assertEquals(ICX, result.asInteger());
        BigInteger fee = tr.getStepUsed().multiply(tr.getStepPrice());
        ensureIcxBalance(txHandler, ownerWallet.getAddress(), ownerBalance, ownerBalance.subtract(ICX).subtract(fee));
        ensureIcxBalance(txHandler, apiScore.getAddress(), BigInteger.ZERO, ICX);
        LOG.infoExiting();

        LOG.infoEntering("getValue", "query");
        result = apiScore.call("getValueQuery", null);
        LOG.info("expected (" + "0" + "), got (" + result.asInteger() + ")");
        assertEquals(BigInteger.ZERO, result.asInteger());
        LOG.infoExiting();

        // getBalance
        LOG.infoEntering("getBalance", "check owner balance");
        ownerBalance = txHandler.getBalance(ownerWallet.getAddress());
        RpcObject params = new RpcObject.Builder()
                .put("address", new RpcValue(ownerWallet.getAddress()))
                .build();
        tr = apiScore.invokeAndWaitResult(caller, "getBalance", params, null, Constants.DEFAULT_STEPS);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + ownerBalance + "), got (" + result.asInteger() + ")");
        assertEquals(ownerBalance, result.asInteger());
        LOG.infoExiting();

        LOG.infoEntering("getBalance", "query");
        result = apiScore.call("getBalanceQuery", params);
        LOG.info("expected (" + ownerBalance + "), got (" + result.asInteger() + ")");
        assertEquals(ownerBalance, result.asInteger());
        LOG.infoExiting();

        LOG.infoEntering("getBalance", "check score balance");
        tr = apiScore.invokeAndWaitResult(caller, "getBalance", null, null, Constants.DEFAULT_STEPS);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + ICX + "), got (" + result.asInteger() + ")");
        assertEquals(ICX, result.asInteger());
        LOG.infoExiting();

        LOG.infoEntering("getBalance", "query");
        result = apiScore.call("getBalanceQuery", null);
        LOG.info("expected (" + ICX + "), got (" + result.asInteger() + ")");
        assertEquals(ICX, result.asInteger());
        LOG.infoExiting();
    }

    @Test
    public void testAPIForHash() throws Exception {
        Score apiScore = deployAPITest();
        TransactionResult tr;
        RpcItem result = RpcValue.NULL;

        Map<BigInteger, String> algoMap = Map.of(
                BigInteger.ZERO, "SHA3_256",
                BigInteger.ONE, "SHA256"
        );
        for (BigInteger algorithm : algoMap.keySet()) {
            LOG.infoEntering("computeHash", "invoke - " + algoMap.get(algorithm));
            byte[] data = "Hello world".getBytes();
            RpcObject params = new RpcObject.Builder()
                    .put("algorithm", new RpcValue(algorithm))
                    .put("data", new RpcValue(data))
                    .build();
            tr = apiScore.invokeAndWaitResult(caller, "computeHash", params);
            assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
            for (TransactionResult.EventLog e : tr.getEventLogs()) {
                result = e.getData().get(0);
            }
            Bytes expected;
            if (algorithm.equals(BigInteger.ZERO)) {
                expected = new Bytes(Crypto.sha3_256(data));
            } else {
                expected = new Bytes(Crypto.sha256(data));
            }
            LOG.info("expected (" + expected + "), got (" + result.asString() + ")");
            assertEquals(expected.toString(), result.asString());
            LOG.infoExiting();

            LOG.infoEntering("computeHash", "query - " + algoMap.get(algorithm));
            result = apiScore.call("computeHashQuery", params);
            LOG.info("expected (" + expected + "), got (" + result.asString() + ")");
            assertEquals(expected.toString(), result.asString());
            LOG.infoExiting();
        }
    }

    @Test
    public void testAPIForRecoverKey() throws Exception {
        Score apiScore = deployAPITest();
        TransactionResult tr;
        RpcItem result = RpcValue.NULL;

        // invoke a transaction to be verified later
        byte[] data = "Hello world".getBytes();
        RpcObject params = new RpcObject.Builder()
                .put("algorithm", new RpcValue(BigInteger.ZERO))
                .put("data", new RpcValue(data))
                .build();
        tr = apiScore.invokeAndWaitResult(caller, "computeHash", params);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());

        // extract the necessary data for the verification
        ConfirmedTransaction tx = iconService.getTransaction(tr.getTxHash()).execute();
        RpcObject.Builder builder = new RpcObject.Builder();
        RpcObject props = tx.getProperties();
        for (String key : props.keySet()) {
            List<String> excludeKeys = List.of("blockHash", "blockHeight", "txHash", "txIndex", "signature");
            if (!excludeKeys.contains(key)) {
                builder.put(key, props.getItem(key));
            }
        }
        String serializedData = SignedTransaction.TransactionSerializer.serialize(builder.build());
        byte[] msgHash = Crypto.sha3_256(serializedData.getBytes());
        byte[] signature = new Base64(props.getItem("signature").asString()).decode();

        // recoverKey
        LOG.infoEntering("recoverKey", "invoke - uncompressed");
        params = new RpcObject.Builder()
                .put("msgHash", new RpcValue(msgHash))
                .put("signature", new RpcValue(signature))
                .put("compressed", new RpcValue(false))
                .build();
        tr = apiScore.invokeAndWaitResult(caller, "recoverKey", params);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + caller.getPublicKey() + "), got (" + result.asString() + ")");
        assertEquals(caller.getPublicKey().toString(), result.asString());
        LOG.infoExiting();

        LOG.infoEntering("getAddressFromKey", "invoke - uncompressed");
        params = new RpcObject.Builder()
                .put("publicKey", new RpcValue(result.asByteArray()))
                .build();
        tr = apiScore.invokeAndWaitResult(caller, "getAddressFromKey", params);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + caller.getAddress() + "), got (" + result.asString() + ")");
        assertEquals(caller.getAddress().toString().substring(2), result.asString().substring(4));
        LOG.infoExiting();

        LOG.infoEntering("recoverKey", "invoke - compressed");
        params = new RpcObject.Builder()
                .put("msgHash", new RpcValue(msgHash))
                .put("signature", new RpcValue(signature))
                .put("compressed", new RpcValue(true))
                .build();
        tr = apiScore.invokeAndWaitResult(caller, "recoverKey", params);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("got (" + result.asString() + ")");
        List<Byte> prefixes = List.of((byte) 0x02, (byte) 0x03);
        assertTrue(prefixes.contains(result.asByteArray()[0]));
        LOG.infoExiting();

        LOG.infoEntering("getAddressFromKey", "invoke - compressed");
        params = new RpcObject.Builder()
                .put("publicKey", new RpcValue(result.asByteArray()))
                .build();
        tr = apiScore.invokeAndWaitResult(caller, "getAddressFromKey", params);
        assertEquals(Constants.STATUS_SUCCESS, tr.getStatus());
        for (TransactionResult.EventLog e : tr.getEventLogs()) {
            result = e.getData().get(0);
        }
        LOG.info("expected (" + caller.getAddress() + "), got (" + result.asString() + ")");
        assertEquals(caller.getAddress().toString().substring(2), result.asString().substring(4));
        LOG.infoExiting();

        LOG.infoEntering("recoverKey", "query");
        params = new RpcObject.Builder()
                .put("msgHash", new RpcValue(msgHash))
                .put("signature", new RpcValue(signature))
                .put("compressed", new RpcValue(false))
                .build();
        RpcItem publicKey = apiScore.call("recoverKeyQuery", params);
        LOG.info("expected (" + caller.getPublicKey() + "), got (" + publicKey.asString() + ")");
        assertEquals(caller.getPublicKey().toString(), publicKey.asString());
        LOG.infoExiting();

        LOG.infoEntering("getAddressFromKey", "query");
        params = new RpcObject.Builder()
                .put("publicKey", new RpcValue(publicKey.asByteArray()))
                .build();
        RpcItem address = apiScore.call("getAddressFromKeyQuery", params);
        LOG.info("expected (" + caller.getAddress() + "), got (" + address.asAddress() + ")");
        assertEquals(caller.getAddress(), address.asAddress());
        LOG.infoExiting();
    }

    @Test
    public void testAPIForDeploy() throws Exception {
        LOG.infoEntering("deploy", "DeployScore");
        var score = txHandler.deploy(ownerWallet, DeployScore.class, null);
        LOG.info("scoreAddress = " + score.getAddress());
        LOG.infoExiting();

        LOG.infoEntering("invoke", "deploy API");
        var classes = new Class<?>[]{APITest.class};
        byte[] jarBytes = txHandler.makeJar(classes[0].getName(), classes);
        RpcObject params = new RpcObject.Builder()
                .put("content", new RpcValue(jarBytes))
                .build();
        var txres = txHandler.getResult(
                score.invoke(ownerWallet, "deploySingle", params));
        assertSuccess(txres);
        TransactionResult.EventLog event = score.findEventLog(txres, "EmitScoreAddress(Address)");
        assertNotNull(event);
        Address scoreAddress = event.getIndexed().get(1).asAddress();
        LOG.info("scoreAddress = " + scoreAddress);
        LOG.infoExiting();

        LOG.infoEntering("call", "directly");
        var apiScore = new Score(txHandler, scoreAddress);
        var res = apiScore.call("getOwnerQuery", null);
        LOG.info("getOwner: expected (" + score.getAddress() + "), got (" +  res.asAddress() + ")");
        assertEquals(score.getAddress(), res.asAddress());
        res = apiScore.call("getAddressQuery", null);
        LOG.info("getAddress: expected (" + scoreAddress + "), got (" +  res.asAddress() + ")");
        assertEquals(scoreAddress, res.asAddress());
        LOG.infoExiting();

        LOG.infoEntering("call", "indirectly");
        res = score.call("getOwner", null);
        LOG.info("getOwner: expected (" + score.getAddress() + "), got (" +  res.asAddress() + ")");
        assertEquals(score.getAddress(), res.asAddress());
        res = score.call("getAddress", null);
        LOG.info("getAddress: expected (" + scoreAddress + "), got (" +  res.asAddress() + ")");
        assertEquals(scoreAddress, res.asAddress());
        LOG.infoExiting();

        LOG.infoEntering("invoke", "deploy APIs twice in a transaction");
        txres = txHandler.getResult(
                score.invoke(ownerWallet, "deployMultiple", params));
        assertSuccess(txres);
        LOG.infoExiting();

        LOG.infoEntering("invoke", "update API");
        classes = new Class<?>[]{HelloWorld.class};
        jarBytes = txHandler.makeJar(classes[0].getName(), classes);
        params = new RpcObject.Builder()
                .put("target", new RpcValue(scoreAddress))
                .put("content", new RpcValue(jarBytes))
                .put("name", new RpcValue("Alice"))
                .build();
        txres = txHandler.getResult(
                score.invoke(ownerWallet, "updateSingle", params));
        assertSuccess(txres);
        LOG.infoExiting();

        LOG.infoEntering("call", "updated methods");
        assertThrows(RpcError.class, () -> apiScore.call("getOwnerQuery", null));
        res = apiScore.call("name", null);
        assertEquals("Alice", res.asString());
        LOG.infoExiting();
    }

    @Test
    public void deployInvalidJar() throws Exception {
        LOG.infoEntering("deploy", "directly");
        var classes = new Class<?>[]{APITest.class};
        byte[] jarBytes = txHandler.makeJar(classes[0].getName(), classes);
        int len = jarBytes.length;
        for (int i = 2; i <= 256; i *= 2) {
            int modLen = len / i;
            LOG.info("len=" + len + ", modLen=" + modLen);
            var content = new byte[len];
            System.arraycopy(jarBytes, 0, content, 0, content.length);
            var garbage = getRandomBytes(modLen);
            System.arraycopy(garbage, 0, content, modLen, garbage.length);
            var hash = txHandler.doDeploy(ownerWallet, content,
                    Constants.CHAINSCORE_ADDRESS, null,
                    Constants.DEFAULT_STEPS, Constants.CONTENT_TYPE_JAVA);
            assertFailure(txHandler.getResult(hash));
        }
        LOG.infoExiting();

        LOG.infoEntering("deploy", "indirectly");
        var deployScore = txHandler.deploy(ownerWallet, DeployScore.class, null);
        LOG.info("scoreAddress = " + deployScore.getAddress());
        for (int i = 2; i <= 256; i *= 2) {
            int modLen = len / i;
            LOG.info("len=" + len + ", modLen=" + modLen);
            var content = new byte[len];
            System.arraycopy(jarBytes, 0, content, 0, content.length);
            var garbage = getRandomBytes(modLen);
            System.arraycopy(garbage, 0, content, modLen, garbage.length);
            RpcObject params = new RpcObject.Builder()
                    .put("content", new RpcValue(content))
                    .build();
            var txres = txHandler.getResult(
                    deployScore.invoke(ownerWallet, "deploySingle", params));
            assertFailure(txres);
            assertEquals(0x21, txres.getFailure().getCode().intValue());
        }
        LOG.infoExiting();
    }
}
