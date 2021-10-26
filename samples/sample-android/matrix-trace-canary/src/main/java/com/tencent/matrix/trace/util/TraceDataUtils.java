/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.matrix.trace.util;

import android.util.Log;

import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.core.AppMethodBeat;
import com.tencent.matrix.trace.items.MethodItem;
import com.tencent.matrix.util.MatrixLog;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

//ok了
public class TraceDataUtils {

    private static final String TAG = "Matrix.TraceDataUtils";

    // METHOD_ID_DISPATCH 1 2 3 4 5 6 6 5 4 3 2 1 METHOD_ID_DISPATCH
    // METHOD_ID_DISPATCH 1 2 3 4 5 6 6 5 4 3

    /**
     *
     * @param buffer
     * @param result
     * @param isStrict 如果堆栈不齐，是否补堆栈，补齐堆栈核心是计算方法时间
     * @param endTime
     */
    public static void structuredDataToStack(long[] buffer, LinkedList<MethodItem> result, boolean isStrict, long endTime) {
        long lastInId = 0L;
        int depth = 0;
        LinkedList<Long> rawData = new LinkedList<>();
        boolean isBegin = !isStrict;

        for (long trueId : buffer) {
            if (0 == trueId) {
                continue;
            }
            if (isStrict) {
                if (isIn(trueId) && AppMethodBeat.METHOD_ID_DISPATCH == getMethodId(trueId)) {
                    isBegin = true;
                }

                if (!isBegin) {
                    MatrixLog.d(TAG, "never begin! pass this method[%s]", getMethodId(trueId));
                    continue;
                }

            }
            if (isIn(trueId)) {
                lastInId = getMethodId(trueId);
                if (lastInId == AppMethodBeat.METHOD_ID_DISPATCH) {
                    depth = 0;
                }
                depth++;
                rawData.push(trueId);
            } else {
                //6
                int outMethodId = getMethodId(trueId);
                //METHOD_ID_DISPATCH 1 2 3 4 5 6
                if (!rawData.isEmpty()) {
                    //6
                    long in = rawData.pop();
                    depth--;
                    int inMethodId;
                    LinkedList<Long> tmp = new LinkedList<>();
                    //tmp add 6，tmp 6
                    tmp.add(in);
                    //6==6，不满足这个条件
                    while ((inMethodId = getMethodId(in)) != outMethodId && !rawData.isEmpty()) {
                        //找到和outMethodId一致的in
                        MatrixLog.w(TAG, "pop inMethodId[%s] to continue match ouMethodId[%s]", inMethodId, outMethodId);
                        in = rawData.pop();
                        depth--;
                        tmp.add(in);
                    }
                    //6==6，不满足这个条件
                    if (inMethodId != outMethodId
                            && inMethodId == AppMethodBeat.METHOD_ID_DISPATCH) {
                        //如果到METHOD_ID_DISPATCH还没找到，则放弃outMethodId方法
                        MatrixLog.e(TAG, "inMethodId[%s] != outMethodId[%s] throw this outMethodId!", inMethodId, outMethodId);
                        rawData.addAll(tmp);
                        depth += rawData.size();
                        continue;
                    }

                    long outTime = getTime(trueId);
                    long inTime = getTime(in);
                    long during = outTime - inTime;
                    if (during < 0) {
                        MatrixLog.e(TAG, "[structuredDataToStack] trace during invalid:%d", during);
                        rawData.clear();
                        result.clear();
                        return;
                    }
                    MethodItem methodItem = new MethodItem(outMethodId, (int) during, depth);
                    //添加6方法
                    addMethodItem(result, methodItem);
                } else {
                    MatrixLog.w(TAG, "[structuredDataToStack] method[%s] not found in! ", outMethodId);
                }
            }
        }

        //堆栈可能不全，这个时候补堆栈，
        while (!rawData.isEmpty() && isStrict) {
            long trueId = rawData.pop();
            int methodId = getMethodId(trueId);
            boolean isIn = isIn(trueId);
            long inTime = getTime(trueId) + AppMethodBeat.getDiffTime();
            MatrixLog.w(TAG, "[structuredDataToStack] has never out method[%s], isIn:%s, inTime:%s, endTime:%s,rawData size:%s",
                    methodId, isIn, inTime, endTime, rawData.size());
            if (!isIn) {
                MatrixLog.e(TAG, "[structuredDataToStack] why has out Method[%s]? is wrong! ", methodId);
                continue;
            }
            //核心，计算时间
            MethodItem methodItem = new MethodItem(methodId, (int) (endTime
                    - inTime), rawData.size());
            addMethodItem(result, methodItem);
        }
        TreeNode root = new TreeNode(null, null);
        int count = stackToTree(result, root);
        MatrixLog.i(TAG, "stackToTree: count=%s", count);
        result.clear();
        treeToStack(root, result);
    }

    //region ok
    private static boolean isIn(long trueId) {
        return ((trueId >> 63) & 0x1) == 1;
    }

    private static long getTime(long trueId) {
        return trueId & 0x7FFFFFFFFFFL;
    }

    private static int getMethodId(long trueId) {
        return (int) ((trueId >> 43) & 0xFFFFFL);
    }

    /**
     * 递归打印TreeNode，存入ss里
     * @param root
     * @param depth
     * @param ss
     * @param prefixStr
     */
    public static void printTree(TreeNode root, int depth, StringBuilder ss, String prefixStr) {
        StringBuilder empty = new StringBuilder(prefixStr);

        for (int i = 0; i <= depth; i++) {
            empty.append("    ");
        }
        for (int i = 0; i < root.children.size(); i++) {
            TreeNode node = root.children.get(i);
            //methodId【时间】
            ss.append(empty.toString()).append(node.item.methodId).append("[").append(node.item.durTime).append("]").append("\n");
            if (!node.children.isEmpty()) {
                printTree(node, depth + 1, ss, prefixStr);
            }
        }
    }
    //endregion

    private static int addMethodItem(LinkedList<MethodItem> resultStack, MethodItem item) {
        if (AppMethodBeat.isDev) {
            Log.v(TAG, "method:" + item);
        }
        MethodItem last = null;
        if (!resultStack.isEmpty()) {
            last = resultStack.peek();
        }
        if (null != last && last.methodId == item.methodId && last.depth == item.depth
                && 0 != item.depth) {
            //todo 为什么判断item.durTime == Constants.DEFAULT_ANR
            item.durTime = item.durTime == Constants.DEFAULT_ANR ? last.durTime : item.durTime;
            last.mergeMore(item.durTime);
            return last.durTime;
        } else {
            resultStack.push(item);
            return item.durTime;
        }
    }

    /**
     * 将TreeNode保存到list
     * 深度优先遍历
     * @param root
     * @param list
     */
    private static void treeToStack(TreeNode root, LinkedList<MethodItem> list) {
        for (int i = 0; i < root.children.size(); i++) {
            TreeNode node = root.children.get(i);
            if (null == node) {
                continue;
            }
            if (node.item != null) {
                list.add(node.item);
            }
            if (!node.children.isEmpty()) {
                treeToStack(node, list);
            }
        }
    }

    /**
     * Structured the method stack as a tree Data structure
     *
     * @param resultStack
     * @return
     * 
     *           1
     *       2        3
     *     4  5      6 7
     */
    public static int stackToTree(LinkedList<MethodItem> resultStack, TreeNode root) {
        TreeNode lastNode = null;
        ListIterator<MethodItem> iterator = resultStack.listIterator(0);
        int count = 0;
        while (iterator.hasNext()) {
            TreeNode node = new TreeNode(iterator.next(), lastNode);
            count++;
            if (null == lastNode && node.depth() != 0) {
                MatrixLog.e(TAG, "[stackToTree] begin error! why the first node'depth is not 0!");
                return 0;
            }
            int depth = node.depth();
            if (lastNode == null || depth == 0) {
                root.add(node);
            } else if (lastNode.depth() >= depth) {
                //这里是指的回退
                while (null != lastNode && lastNode.depth() > depth) {
                    lastNode = lastNode.father;
                }
                if (lastNode != null && lastNode.father != null) {
                    node.father = lastNode.father;
                    lastNode.father.add(node);
                }
            } else {
                lastNode.add(node);
            }
            lastNode = node;
        }
        return count;
    }

    public static long stackToString(LinkedList<MethodItem> stack, StringBuilder reportBuilder, StringBuilder logcatBuilder) {
        logcatBuilder.append("|*\t\tTraceStack:").append("\n");
        logcatBuilder.append("|*\t\t[id count cost]").append("\n");
        Iterator<MethodItem> listIterator = stack.iterator();
        long stackCost = 0; // fix cost
        while (listIterator.hasNext()) {
            MethodItem item = listIterator.next();
            reportBuilder.append(item.toString()).append('\n');
            logcatBuilder.append("|*\t\t").append(item.print()).append('\n');

            if (stackCost < item.durTime) {
                stackCost = item.durTime;
            }
        }
        return stackCost;
    }

    //region nouse
    public static int countTreeNode(TreeNode node) {
        int count = node.children.size();
        Iterator<TreeNode> iterator = node.children.iterator();
        while (iterator.hasNext()) {
            count += countTreeNode(iterator.next());
        }
        return count;
    }

    public static void printTree(TreeNode root, StringBuilder print) {
        print.append("|*   TraceStack: ").append("\n");
        printTree(root, 0, print, "|*        ");
    }

    @Deprecated
    public static String getTreeKey(List<MethodItem> stack, final int targetCount) {
        StringBuilder ss = new StringBuilder();
        final List<MethodItem> tmp = new LinkedList<>(stack);
        trimStack(tmp, targetCount, new TraceDataUtils.IStructuredDataFilter() {
            @Override
            public boolean isFilter(long during, int filterCount) {
                return during < filterCount * Constants.TIME_UPDATE_CYCLE_MS;
            }

            @Override
            public int getFilterMaxCount() {
                return Constants.FILTER_STACK_MAX_COUNT;
            }

            @Override
            public void fallback(List<MethodItem> stack, int size) {
                MatrixLog.w(TAG, "[getTreeKey] size:%s targetSize:%s", size, targetCount);
                Iterator iterator = stack.listIterator(Math.min(size, targetCount));
                while (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        });
        for (MethodItem item : tmp) {
            ss.append(item.methodId + "|");
        }
        return ss.toString();
    }
    //endregion



    public static void trimStack(List<MethodItem> stack, int targetCount, IStructuredDataFilter filter) {
        if (0 > targetCount) {
            stack.clear();
            return;
        }

        int filterCount = 1;
        int curStackSize = stack.size();
        while (curStackSize > targetCount) {
            ListIterator<MethodItem> iterator = stack.listIterator(stack.size());
            //从后往前压缩，删除，最多压缩60次
            while (iterator.hasPrevious()) {
                MethodItem item = iterator.previous();
                if (filter.isFilter(item.durTime, filterCount)) {
                    iterator.remove();
                    curStackSize--;
                    if (curStackSize <= targetCount) {
                        return;
                    }
                }
            }
            curStackSize = stack.size();
            filterCount++;
            if (filter.getFilterMaxCount() < filterCount) {
                break;
            }
        }
        int size = stack.size();
        if (size > targetCount) {
            //最后保存30个堆栈
            filter.fallback(stack, size);
        }
    }


    public static String getTreeKey(List<MethodItem> stack, long stackCost) {
        StringBuilder ss = new StringBuilder();
        long allLimit = (long) (stackCost * Constants.FILTER_STACK_KEY_ALL_PERCENT);

        LinkedList<MethodItem> sortList = new LinkedList<>();

        for (MethodItem item : stack) {
            if (item.durTime >= allLimit) {
                sortList.add(item);
            }
        }

        Collections.sort(sortList, new Comparator<MethodItem>() {
            @Override
            public int compare(MethodItem o1, MethodItem o2) {
                return Integer.compare((o2.depth + 1) * o2.durTime, (o1.depth + 1) * o1.durTime);
            }
        });

        if (sortList.isEmpty() && !stack.isEmpty()) {
            MethodItem root = stack.get(0);
            sortList.add(root);
        } else if (sortList.size() > 1
                && sortList.peek().methodId == AppMethodBeat.METHOD_ID_DISPATCH) {
            sortList.removeFirst();
        }

        for (MethodItem item : sortList) {
            ss.append(item.methodId + "|");
            break;
        }
        return ss.toString();
    }

    public interface IStructuredDataFilter {
        boolean isFilter(long during, int filterCount);

        int getFilterMaxCount();

        void fallback(List<MethodItem> stack, int size);
    }

    //region TreeNode

    /**
     * it's the node for the stack tree
     */
    public static final class TreeNode {
        MethodItem item;
        TreeNode father;
        LinkedList<TreeNode> children = new LinkedList<>();

        TreeNode(MethodItem item, TreeNode father) {
            this.item = item;
            this.father = father;
        }

        private int depth() {
            return null == item ? 0 : item.depth;
        }

        private void add(TreeNode node) {
            children.addFirst(node);//为什么addFirst
        }

        private boolean isLeaf() {
            return children.isEmpty();
        }
    }
    //endregion

}
