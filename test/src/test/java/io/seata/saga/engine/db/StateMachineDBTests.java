/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.saga.engine.db;

import io.seata.common.LockAndCallback;
import io.seata.common.exception.FrameworkErrorCode;
import io.seata.common.exception.StoreException;
import io.seata.core.context.RootContext;
import io.seata.core.exception.TransactionException;
import io.seata.core.model.GlobalStatus;
import io.seata.saga.engine.AsyncCallback;
import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.engine.exception.EngineExecutionException;
import io.seata.saga.engine.impl.DefaultStateMachineConfig;
import io.seata.saga.engine.mock.DemoService.Engineer;
import io.seata.saga.engine.mock.DemoService.People;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.statelang.domain.DomainConstants;
import io.seata.saga.statelang.domain.ExecutionStatus;
import io.seata.saga.statelang.domain.StateMachineInstance;
import io.seata.tm.api.GlobalTransaction;
import io.seata.tm.api.GlobalTransactionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * State machine tests with db log store
 * @author lorne.cl
 */
public class StateMachineDBTests extends AbstractServerTest {

    private static StateMachineEngine stateMachineEngine;

    private static int sleepTime = 1500;

    private static int sleepTimeLong = 2500;

    @BeforeAll
    public static void initApplicationContext() throws InterruptedException {

        startSeataServer();

        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath:saga/spring/statemachine_engine_db_test.xml");
        stateMachineEngine = applicationContext.getBean("stateMachineEngine", StateMachineEngine.class);
    }

    @AfterAll
    public static void destory() throws InterruptedException {
        stopSeataServer();
    }

    private GlobalTransaction getGlobalTransaction(StateMachineInstance instance) {
        GlobalTransaction globalTransaction = null;
        Map<String, Object> params = instance.getContext();
        if (params != null) {
            globalTransaction = (GlobalTransaction) params.get(DomainConstants.VAR_NAME_GLOBAL_TX);
        }
        if (globalTransaction == null) {
            try {
                globalTransaction = GlobalTransactionContext.reload(instance.getId());
            } catch (TransactionException e) {
                e.printStackTrace();
            }
        }
        return globalTransaction;
    }

    @Test
    public void testSimpleStateMachine() {

        stateMachineEngine.start("simpleTestStateMachine", null, new HashMap<>());
    }

    @Test
    public void testSimpleStateMachineWithChoice() {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("a", 1);

        String stateMachineName = "simpleChoiceTestStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-1 :" + cost);

        start = System.currentTimeMillis();
        paramMap.put("a", 2);
        inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-2 :" + cost);
    }

    @Test
    public void testSimpleStateMachineWithChoiceNoDefault() {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("a", 3);

        String stateMachineName = "simpleChoiceNoDefaultTestStateMachine";

        StateMachineInstance inst = null;
        try {
            inst = stateMachineEngine.start(stateMachineName, null, paramMap);
        } catch (EngineExecutionException e) {
            Assertions.assertEquals(FrameworkErrorCode.StateMachineNoChoiceMatched, e.getErrcode());
            e.printStackTrace(System.out);
        }
        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + (inst != null ? inst.getId() : null) + " cost3-3 :" + cost);
    }

    @Test
    public void testSimpleStateMachineWithChoiceAndEnd() {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);

        String stateMachineName = "simpleChoiceAndEndTestStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-4 :" + cost);

        start = System.currentTimeMillis();

        paramMap.put("a", 3);
        inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-5 :" + cost);
    }

    @Test
    public void testSimpleInputAssignmentStateMachine() {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);

        String stateMachineName = "simpleInputAssignmentStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        String businessKey = inst.getStateList().get(0).getBusinessKey();
        Assertions.assertNotNull(businessKey);
        System.out.println("====== businessKey :" + businessKey);

        String contextBusinessKey = (String) inst.getEndParams().get(
                inst.getStateList().get(0).getName() + DomainConstants.VAR_NAME_BUSINESSKEY);
        Assertions.assertNotNull(contextBusinessKey);
        System.out.println("====== context businessKey :" + businessKey);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-6 :" + cost);
    }

    @Test
    public void testSimpleCatchesStateMachine() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleCachesStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-7 :" + cost);

        Assertions.assertNotNull(inst.getException());
        Assertions.assertEquals(ExecutionStatus.FA, inst.getStatus());

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        Assertions.assertEquals(GlobalStatus.Finished, globalTransaction.getStatus());
    }

    @Test
    public void testSimpleRetryStateMachine() {

        long start  = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleRetryStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-8 :" + cost);

        Assertions.assertNotNull(inst.getException());
        Assertions.assertEquals(ExecutionStatus.FA, inst.getStatus());
    }

    @Test
    public void testStatusMatchingStateMachine() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleStatusMatchingStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-9 :" + cost);

        Assertions.assertNotNull(inst.getException());
        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        System.out.println(globalTransaction.getStatus());
        Assertions.assertEquals(GlobalStatus.CommitRetrying, globalTransaction.getStatus());
    }

    @Test
    public void testStateMachineWithComplexParams() {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        People people = new People();
        people.setName("lilei");
        people.setAge(18);

        Engineer engineer = new Engineer();
        engineer.setName("programmer");

        paramMap.put("people", people);
        paramMap.put("career", engineer);

        String stateMachineName = "simpleStateMachineWithComplexParamsJackson";

        StateMachineInstance instance = stateMachineEngine.start(stateMachineName, null, paramMap);

        People peopleResult = (People) instance.getEndParams().get("complexParameterMethodResult");
        Assertions.assertNotNull(peopleResult);
        Assertions.assertEquals(people.getName(), peopleResult.getName());

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + instance.getId() + " cost3-10 :" + cost);

        Assertions.assertEquals(ExecutionStatus.SU, instance.getStatus());
    }

    @Test
    public void testCompensationStateMachine() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleCompensationStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-11 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());
        Assertions.assertEquals(ExecutionStatus.SU, inst.getCompensationStatus());

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        //End with Rollbacked = Finished
        Assertions.assertEquals(GlobalStatus.Finished, globalTransaction.getStatus());
    }

    @Test
    public void testCompensationAndSubStateMachine() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 2);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleStateMachineWithCompensationAndSubMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-12 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        Assertions.assertEquals(GlobalStatus.CommitRetrying, globalTransaction.getStatus());
    }

    @Test
    public void testCompensationAndSubStateMachineLayout() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 2);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleStateMachineWithCompensationAndSubMachine_layout";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-13 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        Assertions.assertEquals(GlobalStatus.CommitRetrying, globalTransaction.getStatus());
    }

    @Test
    public void testCompensationStateMachineForRecovery() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("fooThrowExceptionRandomly", "true");
        paramMap.put("barThrowExceptionRandomly", "true");
        paramMap.put("compensateFooThrowExceptionRandomly", "true");
        paramMap.put("compensateBarThrowExceptionRandomly", "true");

        String stateMachineName = "simpleCompensationStateMachineForRecovery";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-14 :" + cost);

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());

        // waiting for global transaction recover
        while (!(ExecutionStatus.SU.equals(inst.getStatus()) || ExecutionStatus.SU.equals(inst.getCompensationStatus()))) {
            System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());
            Thread.sleep(1000);
            inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        }
    }

    @Test
    public void testReloadStateMachineInstance() {
        StateMachineInstance instance = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(
                "10.15.232.93:8091:2019567124");
        System.out.println(instance);
    }

    @Test
    public void testSimpleStateMachineWithAsyncState() {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);

        String stateMachineName = "simpleStateMachineWithAsyncState";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-15 :" + cost);

        Assertions.assertEquals(ExecutionStatus.SU, inst.getStatus());

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSimpleCatchesStateMachineAsync() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleCachesStateMachine";

        LockAndCallback lockAndCallback = new LockAndCallback();
        StateMachineInstance inst = stateMachineEngine.startAsync(stateMachineName, null, paramMap, lockAndCallback.getCallback());

        lockAndCallback.waittingForFinish(inst);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-16 :" + cost);

        Assertions.assertNotNull(inst.getException());
        Assertions.assertEquals(ExecutionStatus.FA, inst.getStatus());
    }

    @Test
    public void testSimpleRetryStateMachineAsync() {

        long start  = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleRetryStateMachine";

        LockAndCallback lockAndCallback = new LockAndCallback();
        StateMachineInstance inst = stateMachineEngine.startAsync(stateMachineName, null, paramMap, lockAndCallback.getCallback());

        lockAndCallback.waittingForFinish(inst);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-17 :" + cost);


        Assertions.assertNotNull(inst.getException());
        Assertions.assertEquals(ExecutionStatus.FA, inst.getStatus());
    }

    @Test
    public void testStatusMatchingStateMachineAsync() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleStatusMatchingStateMachine";

        LockAndCallback lockAndCallback = new LockAndCallback();
        StateMachineInstance inst = stateMachineEngine.startAsync(stateMachineName, null, paramMap, lockAndCallback.getCallback());

        lockAndCallback.waittingForFinish(inst);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-18 :" + cost);

        Assertions.assertNotNull(inst.getException());
        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        Assertions.assertEquals(GlobalStatus.CommitRetrying, globalTransaction.getStatus());
    }

    @Disabled("https://github.com/seata/seata/issues/2564")
    public void testCompensationStateMachineAsync() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleCompensationStateMachine";

        LockAndCallback lockAndCallback = new LockAndCallback();
        StateMachineInstance inst = stateMachineEngine.startAsync(stateMachineName, null, paramMap, lockAndCallback.getCallback());

        lockAndCallback.waittingForFinish(inst);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-19 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());
        Assertions.assertEquals(ExecutionStatus.SU, inst.getCompensationStatus());

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        Assertions.assertEquals(GlobalStatus.Finished, globalTransaction.getStatus());
    }

    @Test
    @Disabled("https://github.com/seata/seata/issues/2414#issuecomment-639546811")
    public void simpleChoiceTestStateMachineAsyncConcurrently() throws Exception {

        final CountDownLatch countDownLatch = new CountDownLatch(100);
        final List<Exception> exceptions = new ArrayList<>();

        final AsyncCallback asyncCallback = new AsyncCallback() {
            @Override
            public void onFinished(ProcessContext context, StateMachineInstance stateMachineInstance) {

                countDownLatch.countDown();
            }

            @Override
            public void onError(ProcessContext context, StateMachineInstance stateMachineInstance, Exception exp) {

                countDownLatch.countDown();
                exceptions.add(exp);
            }
        };

        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < 10; j++) {
                        Map<String, Object> paramMap = new HashMap<>();
                        paramMap.put("a", 1);
                        paramMap.put("barThrowException", "false");

                        String stateMachineName = "simpleCompensationStateMachine";

                        try {
                            stateMachineEngine.startAsync(stateMachineName, null, paramMap, asyncCallback);
                        } catch (Exception e) {
                            countDownLatch.countDown();
                            exceptions.add(e);
                        }
                    }
                }
            });
            t.start();
        }

        countDownLatch.await(10000, TimeUnit.MILLISECONDS);
        if (exceptions.size() > 0) {
            Assertions.fail(exceptions.get(0));
        }

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== cost3-20 :" + cost);
    }

    @Test
    @Disabled("https://github.com/seata/seata/issues/2414#issuecomment-651526068")
    public void testCompensationAndSubStateMachineAsync() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 2);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleStateMachineWithCompensationAndSubMachine";

        LockAndCallback lockAndCallback = new LockAndCallback();
        StateMachineInstance inst = stateMachineEngine.startAsync(stateMachineName, null, paramMap, lockAndCallback.getCallback());

        lockAndCallback.waittingForFinish(inst);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-21 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        Assertions.assertEquals(GlobalStatus.CommitRetrying, globalTransaction.getStatus());
    }

    @Test
    @Disabled("https://github.com/seata/seata/issues/2414#issuecomment-640432396")
    public void testCompensationAndSubStateMachineAsyncWithLayout() throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 2);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleStateMachineWithCompensationAndSubMachine_layout";

        LockAndCallback lockAndCallback = new LockAndCallback();
        StateMachineInstance inst = stateMachineEngine.startAsync(stateMachineName, null, paramMap, lockAndCallback.getCallback());

        lockAndCallback.waittingForFinish(inst);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-22 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        Assertions.assertEquals(GlobalStatus.CommitRetrying, globalTransaction.getStatus());
    }

    @Test
    public void testAsyncStartSimpleStateMachineWithAsyncState() {

        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);

        String stateMachineName = "simpleStateMachineWithAsyncState";

        LockAndCallback lockAndCallback = new LockAndCallback();
        StateMachineInstance inst = stateMachineEngine.startAsync(stateMachineName, null, paramMap, lockAndCallback.getCallback());

        lockAndCallback.waittingForFinish(inst);

        Assertions.assertEquals(ExecutionStatus.SU, inst.getStatus());

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-23 :" + cost);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStateMachineTransTimeout() throws Exception {

        ((DefaultStateMachineConfig)stateMachineEngine.getStateMachineConfig()).setTransOperationTimeout(1000);

        //first state timeout
        Map<String, Object> paramMap = new HashMap<>(3);
        paramMap.put("a", 1);

        //timeout rollback after state machine finished (first state success)
        paramMap.put("fooSleepTime", sleepTime);
        doTestStateMachineTransTimeout(paramMap);

        //timeout rollback before state machine finished (first state success)
        paramMap.put("fooSleepTime", sleepTimeLong);
        doTestStateMachineTransTimeout(paramMap);

        //timeout rollback after state machine finished (first state fail)
        paramMap.put("fooSleepTime", sleepTime);
        paramMap.put("fooThrowException", "true");
        doTestStateMachineTransTimeout(paramMap);

        //timeout rollback before state machine finished (first state fail)
        paramMap.put("fooSleepTime", sleepTimeLong);
        paramMap.put("fooThrowException", "true");
        doTestStateMachineTransTimeout(paramMap);


        //last state timeout
        paramMap = new HashMap<>(3);
        paramMap.put("a", 1);

        //timeout rollback after state machine finished (last state success)
        paramMap.put("barSleepTime", sleepTime);
        doTestStateMachineTransTimeout(paramMap);

        //timeout rollback before state machine finished (last state success)
        paramMap.put("barSleepTime", sleepTimeLong);
        doTestStateMachineTransTimeout(paramMap);

        //timeout rollback after state machine finished (last state fail)
        paramMap.put("barSleepTime", sleepTime);
        paramMap.put("barThrowException", "true");
        doTestStateMachineTransTimeout(paramMap);

        //timeout rollback before state machine finished (last state fail)
        paramMap.put("barSleepTime", sleepTimeLong);
        paramMap.put("barThrowException", "true");
        doTestStateMachineTransTimeout(paramMap);

        ((DefaultStateMachineConfig)stateMachineEngine.getStateMachineConfig()).setTransOperationTimeout(60000 * 30);
    }

    @Test
    public void testStateMachineTransTimeoutAsync() throws Exception {

        ((DefaultStateMachineConfig)stateMachineEngine.getStateMachineConfig()).setTransOperationTimeout(1000);

        //first state timeout
        Map<String, Object> paramMap = new HashMap<>(3);
        paramMap.put("a", 1);

        //timeout rollback after state machine finished (first state success)
        paramMap.put("fooSleepTime", sleepTime);
        doTestStateMachineTransTimeoutAsync(paramMap);

        //timeout rollback before state machine finished (first state success)
        paramMap.put("fooSleepTime", sleepTimeLong);
        doTestStateMachineTransTimeoutAsync(paramMap);

        //timeout rollback after state machine finished (first state fail)
        paramMap.put("fooSleepTime", sleepTime);
        paramMap.put("fooThrowException", "true");
        doTestStateMachineTransTimeoutAsync(paramMap);

        //timeout rollback before state machine finished (first state fail)
        paramMap.put("fooSleepTime", sleepTimeLong);
        paramMap.put("fooThrowException", "true");
        doTestStateMachineTransTimeoutAsync(paramMap);


        //last state timeout
        paramMap = new HashMap<>(3);
        paramMap.put("a", 1);

        //timeout rollback after state machine finished (last state success)
        paramMap.put("barSleepTime", sleepTime);
        doTestStateMachineTransTimeoutAsync(paramMap);

        //timeout rollback before state machine finished (last state success)
        paramMap.put("barSleepTime", sleepTimeLong);
        doTestStateMachineTransTimeoutAsync(paramMap);

        //timeout rollback after state machine finished (last state fail)
        paramMap.put("barSleepTime", sleepTime);
        paramMap.put("barThrowException", "true");
        doTestStateMachineTransTimeoutAsync(paramMap);

        //timeout rollback before state machine finished (last state fail)
        paramMap.put("barSleepTime", sleepTimeLong);
        paramMap.put("barThrowException", "true");
        doTestStateMachineTransTimeoutAsync(paramMap);

        ((DefaultStateMachineConfig)stateMachineEngine.getStateMachineConfig()).setTransOperationTimeout(60000 * 30);
    }

    @Test
    public void testStateMachineRecordFailed() {

        String businessKey = "bizKey";

        Assertions.assertDoesNotThrow(() -> stateMachineEngine.startWithBusinessKey("simpleTestStateMachine", null, businessKey, new HashMap<>()));

        // use same biz key to mock exception
        Assertions.assertThrows(StoreException.class, () -> stateMachineEngine.startWithBusinessKey("simpleTestStateMachine", null, businessKey, new HashMap<>()));
        Assertions.assertNull(RootContext.getXID());
    }

    @Test
    public void testSimpleRetryStateAsUpdateMode() throws Exception {
        long start  = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 1);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleUpdateStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-24 :" + cost);

        Assertions.assertNotNull(inst.getException());
        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        Thread.sleep(sleepTime);
        inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        Assertions.assertEquals(2, inst.getStateList().size());
    }

    @Test
    //@Disabled("FIXME")
    public void testSimpleCompensateStateAsUpdateMode() throws Exception {
        long start = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 2);
        paramMap.put("barThrowException", "true");
        paramMap.put("compensateBarThrowException", "true");

        String stateMachineName = "simpleUpdateStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-25 :" + cost);

        Assertions.assertNotNull(inst.getException());
        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        Thread.sleep(sleepTime);
        inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        // FIXME: some times, the size is 4
        Assertions.assertEquals(3, inst.getStateList().size());
    }

    @Test
    public void testSimpleSubRetryStateAsUpdateMode() throws Exception {
        long start  = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 3);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleStateMachineWithCompensationAndSubMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-26 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        Thread.sleep(sleepTime);
        inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());

        Assertions.assertEquals(2, inst.getStateList().size());
    }

    @Test
    public void testSimpleSubCompensateStateAsUpdateMode() throws Exception {
        long start  = System.currentTimeMillis();

        Map<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("a", 4);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleStateMachineWithCompensationAndSubMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-27 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        Thread.sleep(sleepTime);
        inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());

        Assertions.assertEquals(2, inst.getStateList().size());
    }

    @Test
    public void testSimpleStateMachineWithLoop() {
        long start  = System.currentTimeMillis();

        List<Integer> loopList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            loopList.add(i);
        }

        Map<String, Object> paramMap = new HashMap<>(2);
        paramMap.put("a", 1);
        paramMap.put("collection", loopList);

        String stateMachineName = "simpleLoopTestStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-28 :" + cost);

        Assertions.assertEquals(ExecutionStatus.SU, inst.getStatus());
    }

    @Test
    public void testSimpleStateMachineWithLoopForward() throws InterruptedException {
        long start  = System.currentTimeMillis();

        List<Integer> loopList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            loopList.add(i);
        }

        Map<String, Object> paramMap = new HashMap<>(2);
        paramMap.put("a", 1);
        paramMap.put("collection", loopList);
        paramMap.put("fooThrowException", "true");

        String stateMachineName = "simpleLoopTestStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-29 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        Thread.sleep(sleepTime);
        inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());
    }

    @Test
    public void testSimpleStateMachineWithLoopCompensate() {
        long start = System.currentTimeMillis();

        List<Integer> loopList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            loopList.add(i);
        }

        Map<String, Object> paramMap = new HashMap<>(2);
        paramMap.put("a", 1);
        paramMap.put("collection", loopList);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleLoopTestStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-30 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());
        Assertions.assertEquals(ExecutionStatus.SU, inst.getCompensationStatus());
    }

    @Test
    public void testSimpleStateMachineWithLoopCompensateForRecovery() throws InterruptedException {
        long start  = System.currentTimeMillis();

        List<Integer> loopList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            loopList.add(i);
        }

        Map<String, Object> paramMap = new HashMap<>(2);
        paramMap.put("a", 1);
        paramMap.put("collection", loopList);
        paramMap.put("barThrowException", "true");
        paramMap.put("compensateFooThrowException", "true");

        String stateMachineName = "simpleLoopTestStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-31 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());
        Assertions.assertEquals(ExecutionStatus.UN, inst.getCompensationStatus());

        Thread.sleep(sleepTime);

        inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        Assertions.assertEquals(ExecutionStatus.UN, inst.getCompensationStatus());
    }

    @Test
    public void testSimpleStateMachineWithLoopSubMachine() {
        long start = System.currentTimeMillis();

        List<Integer> loopList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            loopList.add(i);
        }

        Map<String, Object> paramMap = new HashMap<>(2);
        paramMap.put("a", 2);
        paramMap.put("collection", loopList);

        String stateMachineName = "simpleLoopTestStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-32 :" + cost);

        Assertions.assertEquals(ExecutionStatus.SU, inst.getStatus());
    }

    @Test
    public void testSimpleStateMachineWithLoopSubMachineForward() throws InterruptedException {
        long start  = System.currentTimeMillis();

        List<Integer> loopList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            loopList.add(i);
        }

        Map<String, Object> paramMap = new HashMap<>(2);
        paramMap.put("a", 2);
        paramMap.put("collection", loopList);
        paramMap.put("barThrowException", "true");

        String stateMachineName = "simpleLoopTestStateMachine";

        StateMachineInstance inst = stateMachineEngine.start(stateMachineName, null, paramMap);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-33 :" + cost);

        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());

        Thread.sleep(sleepTime);
        inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        Assertions.assertEquals(ExecutionStatus.UN, inst.getStatus());
    }

    private void doTestStateMachineTransTimeout(Map<String, Object> paramMap) throws Exception {

        long start = System.currentTimeMillis();

        String stateMachineName = "simpleCompensationStateMachine";

        StateMachineInstance inst;
        try {
            inst = stateMachineEngine.start(stateMachineName, null, paramMap);
        } catch (EngineExecutionException e) {
            e.printStackTrace();

            inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(e.getStateMachineInstanceId());
        }

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-34 :" + cost);

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());

        // waiting for global transaction recover
        while (!ExecutionStatus.SU.equals(inst.getCompensationStatus())) {
            System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());
            Thread.sleep(1000);
            inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        }

        Assertions.assertTrue(ExecutionStatus.UN.equals(inst.getStatus())
                || ExecutionStatus.SU.equals(inst.getStatus()));
        Assertions.assertEquals(ExecutionStatus.SU, inst.getCompensationStatus());
    }

    private void doTestStateMachineTransTimeoutAsync(Map<String, Object> paramMap) throws Exception {

        long start = System.currentTimeMillis();

        String stateMachineName = "simpleCompensationStateMachine";

        LockAndCallback lockAndCallback = new LockAndCallback();
        StateMachineInstance inst = stateMachineEngine.startAsync(stateMachineName, null, paramMap, lockAndCallback.getCallback());

        lockAndCallback.waittingForFinish(inst);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-35 :" + cost);

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());

        // waiting for global transaction recover
        while (!ExecutionStatus.SU.equals(inst.getCompensationStatus())) {
            System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());
            Thread.sleep(1000);
            inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        }

        Assertions.assertTrue(ExecutionStatus.UN.equals(inst.getStatus())
                || ExecutionStatus.SU.equals(inst.getStatus()));
        Assertions.assertEquals(ExecutionStatus.SU, inst.getCompensationStatus());
    }


    @Disabled
    public void testStateMachineCustomRecoverStrategyOnTimeout() throws Exception {

        ((DefaultStateMachineConfig)stateMachineEngine.getStateMachineConfig()).setTransOperationTimeout(1000);

        //first state timeout
        Map<String, Object> paramMap = new HashMap<>(3);
        paramMap.put("a", 1);

        //timeout forward after state machine finished (first state success)
        paramMap.put("fooSleepTime", sleepTime);
        doTestStateMachineCustomRecoverStrategyOnTimeout(paramMap);

        //timeout forward before state machine finished (first state success)
        paramMap.put("fooSleepTime", sleepTimeLong);
        doTestStateMachineCustomRecoverStrategyOnTimeout(paramMap);

        //timeout forward after state machine finished (first state fail randomly)
        paramMap.put("fooSleepTime", sleepTime);
        paramMap.put("fooThrowExceptionRandomly", "true");
        doTestStateMachineCustomRecoverStrategyOnTimeout(paramMap);

        //timeout forward before state machine finished (first state fail randomly)
        paramMap.put("fooSleepTime", sleepTimeLong);
        paramMap.put("fooThrowExceptionRandomly", "true");
        doTestStateMachineCustomRecoverStrategyOnTimeout(paramMap);


        //last state timeout
        paramMap = new HashMap<>(3);
        paramMap.put("a", 1);

        //timeout forward after state machine finished (last state success)
        paramMap.put("barSleepTime", sleepTime);
        doTestStateMachineCustomRecoverStrategyOnTimeout(paramMap);

        //timeout forward before state machine finished (last state success)
        paramMap.put("barSleepTime", sleepTimeLong);
        doTestStateMachineCustomRecoverStrategyOnTimeout(paramMap);

        //timeout forward after state machine finished (last state fail randomly)
        paramMap.put("barSleepTime", sleepTime);
        paramMap.put("barThrowExceptionRandomly", "true");
        doTestStateMachineCustomRecoverStrategyOnTimeout(paramMap);

        //timeout forward before state machine finished (last state fail randomly)
        paramMap.put("barSleepTime", sleepTimeLong);
        paramMap.put("barThrowExceptionRandomly", "true");
        doTestStateMachineCustomRecoverStrategyOnTimeout(paramMap);

        ((DefaultStateMachineConfig)stateMachineEngine.getStateMachineConfig()).setTransOperationTimeout(60000 * 30);
    }

    private void doTestStateMachineCustomRecoverStrategyOnTimeout(Map<String, Object> paramMap) throws Exception {

        long start = System.currentTimeMillis();

        String stateMachineName = "simpleStateMachineWithRecoverStrategy";

        StateMachineInstance inst;
        try {
            inst = stateMachineEngine.start(stateMachineName, null, paramMap);
        } catch (EngineExecutionException e) {
            e.printStackTrace();

            inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(e.getStateMachineInstanceId());
        }

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-36 :" + cost);

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());

        // waiting for global transaction recover
        while (!(ExecutionStatus.SU.equals(inst.getStatus())
                && GlobalStatus.Finished.equals(globalTransaction.getStatus()))) {
            System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());
            System.out.println("====== StateMachineInstanceStatus: " + inst.getStatus());
            Thread.sleep(1000);
            inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        }

        Assertions.assertEquals(ExecutionStatus.SU, inst.getStatus());
        Assertions.assertNull(inst.getCompensationStatus());
    }

    @Disabled
    public void testStateMachineCustomRecoverStrategyOnTimeoutAsync() throws Exception {

        ((DefaultStateMachineConfig)stateMachineEngine.getStateMachineConfig()).setTransOperationTimeout(1000);

        //first state timeout
        Map<String, Object> paramMap = new HashMap<>(3);
        paramMap.put("a", 1);

        //timeout forward after state machine finished (first state success)
        paramMap.put("fooSleepTime", sleepTime);
        doTestStateMachineCustomRecoverStrategyOnTimeoutAsync(paramMap);

        //timeout forward before state machine finished (first state success)
        paramMap.put("fooSleepTime", sleepTimeLong);
        doTestStateMachineCustomRecoverStrategyOnTimeoutAsync(paramMap);

        //timeout forward after state machine finished (first state fail randomly)
        paramMap.put("fooSleepTime", sleepTime);
        paramMap.put("fooThrowExceptionRandomly", "true");
        doTestStateMachineCustomRecoverStrategyOnTimeoutAsync(paramMap);

        //timeout forward before state machine finished (first state fail randomly)
        paramMap.put("fooSleepTime", sleepTimeLong);
        paramMap.put("fooThrowExceptionRandomly", "true");
        doTestStateMachineCustomRecoverStrategyOnTimeoutAsync(paramMap);


        //last state timeout
        paramMap = new HashMap<>(3);
        paramMap.put("a", 1);

        //timeout forward after state machine finished (last state success)
        paramMap.put("barSleepTime", sleepTime);
        doTestStateMachineCustomRecoverStrategyOnTimeoutAsync(paramMap);

        //timeout forward before state machine finished (last state success)
        paramMap.put("barSleepTime", sleepTimeLong);
        doTestStateMachineCustomRecoverStrategyOnTimeoutAsync(paramMap);

        //timeout forward after state machine finished (last state fail randomly)
        paramMap.put("barSleepTime", sleepTime);
        paramMap.put("barThrowExceptionRandomly", "true");
        doTestStateMachineCustomRecoverStrategyOnTimeoutAsync(paramMap);

        //timeout forward before state machine finished (last state fail randomly)
        paramMap.put("barSleepTime", sleepTimeLong);
        paramMap.put("barThrowExceptionRandomly", "true");
        doTestStateMachineCustomRecoverStrategyOnTimeoutAsync(paramMap);

        ((DefaultStateMachineConfig)stateMachineEngine.getStateMachineConfig()).setTransOperationTimeout(60000 * 30);
    }

    private void doTestStateMachineCustomRecoverStrategyOnTimeoutAsync(Map<String, Object> paramMap) throws Exception {

        long start = System.currentTimeMillis();

        String stateMachineName = "simpleStateMachineWithRecoverStrategy";

        LockAndCallback lockAndCallback = new LockAndCallback();
        StateMachineInstance inst = stateMachineEngine.startAsync(stateMachineName, null, paramMap, lockAndCallback.getCallback());

        lockAndCallback.waittingForFinish(inst);

        long cost = System.currentTimeMillis() - start;
        System.out.println("====== XID: " + inst.getId() + " cost3-37 :" + cost);

        GlobalTransaction globalTransaction = getGlobalTransaction(inst);
        Assertions.assertNotNull(globalTransaction);
        System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());

        // waiting for global transaction recover
        while (!(ExecutionStatus.SU.equals(inst.getStatus())
                && GlobalStatus.Finished.equals(globalTransaction.getStatus()))) {
            System.out.println("====== GlobalStatus: " + globalTransaction.getStatus());
            System.out.println("====== StateMachineInstanceStatus: " + inst.getStatus());
            Thread.sleep(1000);
            inst = stateMachineEngine.getStateMachineConfig().getStateLogStore().getStateMachineInstance(inst.getId());
        }

        Assertions.assertEquals(ExecutionStatus.SU, inst.getStatus());
        Assertions.assertNull(inst.getCompensationStatus());
    }
}
