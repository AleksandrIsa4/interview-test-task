package com.vk.dwzkf.test.impl;

import com.vk.dwzkf.test.Accumulator;
import com.vk.dwzkf.test.State;
import com.vk.dwzkf.test.StateObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Roman Shageev
 * @since 12.08.2024
 */
public class AccumulatorImpl implements Accumulator {

    private static final Map<Long, StateObjectExecution> mapProcess = new HashMap<>();

    /**
     * Представляет собой объект совокупность уведомлений одного ID процесса с параметрами
     */
    private static class StateObjectExecution {

        /**
         * Список поступивших уведомлений
         */
        private final List<StateObject> stateObjects = new ArrayList<>();

        /**
         * Последнее показанное уведомление
         */
        private StateObject stateLast;

        /**
         * Номер Последнего показанного уведомления в списке
         */
        private int currentSeq;
        /**
         * Флаг, что было обработано финальное уведомление
         */
        private boolean isFinish;
    }

    @Override
    public void accept(StateObject stateObject) {
        StateObjectExecution stateObjectExecution = mapProcess.computeIfAbsent(stateObject.getProcessId(), s -> new StateObjectExecution());
        if (stateObjectExecution.isFinish) {
            return;
        }
        stateObjectExecution.stateObjects.add(stateObject);
    }

    @Override
    public void acceptAll(List<StateObject> stateObjects) {
        stateObjects.forEach(this::accept);
    }

    @Override
    public List<StateObject> drain(Long processId) {
        StateObjectExecution stateObjectExecution = mapProcess.get(processId);
        if (stateObjectExecution.isFinish) {
            return new ArrayList<>();
        }
        List<StateObject> currentProcessStateObject = stateObjectExecution.stateObjects.subList(stateObjectExecution.currentSeq, stateObjectExecution.stateObjects.size());
        List<StateObject> result = new ArrayList<>();
        if (stateObjectExecution.currentSeq == 0) {
            fillingStart(stateObjectExecution, currentProcessStateObject, result);
        }
        if (stateObjectExecution.currentSeq != 0) {
            fillingMid(stateObjectExecution, currentProcessStateObject, result);
            fillingFinal(stateObjectExecution, currentProcessStateObject, result);
        }
        return result;
    }

    /**
     * Занесение уведомления в заполняемый список для отображения
     *
     * @param stateObjectExecution объект совокупность уведомлений
     * @param stateObject          уведовление для передачи в список
     * @param result               заполняемый список для отображения
     */
    private void inputResult(List<StateObject> result, StateObject stateObject, StateObjectExecution stateObjectExecution) {
        result.add(stateObject);
        stateObjectExecution.stateLast = stateObject;
        stateObjectExecution.currentSeq++;
        if (stateObject.getState() == State.FINAL1 || stateObject.getState() == State.FINAL2) {
            stateObjectExecution.isFinish = true;
        }
    }

    /**
     * Обрабатывает события START
     *
     * @param stateObjectExecution      объект совокупность уведомлений
     * @param currentProcessStateObject список доступных уведомлений для отображения
     * @param result                    заполняемый список для отображения
     */
    private void fillingStart(StateObjectExecution stateObjectExecution, List<StateObject> currentProcessStateObject, List<StateObject> result) {
        currentProcessStateObject.stream()
                .filter(s -> s.getState().equals(State.START1))
                .findFirst()
                .ifPresentOrElse(s -> inputResult(result, s, stateObjectExecution),
                        () -> fillingStart2(stateObjectExecution, currentProcessStateObject, result));
    }

    /**
     * Обрабатывает события START2
     *
     * @param stateObjectExecution      объект совокупность уведомлений
     * @param currentProcessStateObject список доступных уведомлений для отображения
     * @param result                    заполняемый список для отображения
     */
    private void fillingStart2(StateObjectExecution stateObjectExecution, List<StateObject> currentProcessStateObject, List<StateObject> result) {
        currentProcessStateObject.stream()
                .filter(s -> s.getState().equals(State.START2))
                .findFirst()
                .ifPresent(s -> inputResult(result, s, stateObjectExecution));
    }


    /**
     * Обрабатывает события MID
     *
     * @param stateObjectExecution      объект совокупность уведомлений
     * @param currentProcessStateObject список доступных уведомлений для отображения
     * @param result                    заполняемый список для отображения
     */
    private void fillingMid(StateObjectExecution stateObjectExecution, List<StateObject> currentProcessStateObject, List<StateObject> result) {
        if (currentProcessStateObject.stream().anyMatch(s -> s.getState().equals(State.MID1) || s.getState().equals(State.MID2))) {
            List<StateObject> listMid1 = new ArrayList<>(currentProcessStateObject.stream().filter(s -> s.getState().equals(State.MID1)).toList());
            List<StateObject> listMid2 = new ArrayList<>(currentProcessStateObject.stream().filter(s -> s.getState().equals(State.MID2)).toList());
            State lastState = stateObjectExecution.stateLast.getState();
            if (!listMid2.isEmpty() && lastState.equals(State.MID1)) {
                inputResult(result, listMid2.get(0), stateObjectExecution);
                listMid2.remove(0);
            }
            int i;
            for (i = 0; i < Math.min(listMid1.size(), listMid2.size()); i++) {
                inputResult(result, listMid1.get(i), stateObjectExecution);
                inputResult(result, listMid2.get(i), stateObjectExecution);
            }
            if (i < listMid1.size() && !stateObjectExecution.stateLast.getState().equals(State.MID1)) {
                inputResult(result, listMid1.get(i), stateObjectExecution);
            }
        }
    }

    /**
     * Обрабатывает события FINAL
     *
     * @param stateObjectExecution      объект совокупность уведомлений
     * @param currentProcessStateObject список доступных уведомлений для отображения
     * @param result                    заполняемый список для отображения
     */
    private void fillingFinal(StateObjectExecution stateObjectExecution, List<StateObject> currentProcessStateObject, List<StateObject> result) {
        currentProcessStateObject.stream()
                .filter(s -> s.getState().equals(State.FINAL1))
                .findFirst()
                .ifPresentOrElse(s -> inputResult(result, s, stateObjectExecution),
                        () -> fillingFinal2(stateObjectExecution, currentProcessStateObject, result));
    }

    /**
     * Обрабатывает события FINAL2
     *
     * @param stateObjectExecution      объект совокупность уведомлений
     * @param currentProcessStateObject список доступных уведомлений для отображения
     * @param result                    заполняемый список для отображения
     */
    private void fillingFinal2(StateObjectExecution stateObjectExecution, List<StateObject> currentProcessStateObject, List<StateObject> result) {
        currentProcessStateObject.stream()
                .filter(s -> s.getState().equals(State.FINAL2))
                .findFirst()
                .ifPresent(s -> inputResult(result, s, stateObjectExecution));
    }
}

