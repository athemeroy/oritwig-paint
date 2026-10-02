/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UndoStore {

    public interface UndoStoreDelegate {
        void historyChanged();
    }

    private UndoStoreDelegate delegate;
    private Map<UUID, Runnable> uuidToOperationMap = new HashMap<>();
    private List<UUID> operations = new ArrayList<>();

    public synchronized boolean canUndo() {
        return !operations.isEmpty();
    }

    public UndoStoreDelegate getDelegate() {
        return delegate;
    }

    public void setDelegate(UndoStoreDelegate undoStoreDelegate) {
        delegate = undoStoreDelegate;
    }

    public synchronized void registerUndo(UUID uuid, Runnable undoRunnable) {
        uuidToOperationMap.put(uuid, undoRunnable);
        operations.add(uuid);

        notifyOfHistoryChanges();
    }

    public synchronized void unregisterUndo(UUID uuid) {
        uuidToOperationMap.remove(uuid);
        operations.remove(uuid);

        notifyOfHistoryChanges();
    }

    public synchronized void undo() {
        if (operations.size() == 0) {
            return;
        }

        int lastIndex = operations.size() - 1;
        UUID uuid = operations.get(lastIndex);
        Runnable undoRunnable = uuidToOperationMap.get(uuid);
        uuidToOperationMap.remove(uuid);
        operations.remove(lastIndex);

        undoRunnable.run();
        notifyOfHistoryChanges();
    }

    public synchronized void clear() {
        while (!operations.isEmpty()) {
            undo();
        }
    }

    public synchronized void reset() {
        operations.clear();
        uuidToOperationMap.clear();

        notifyOfHistoryChanges();
    }

    private void notifyOfHistoryChanges() {
        Platform.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.historyChanged();
            }
        });
    }
}
