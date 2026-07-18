package com.puckzone.matchmaking.store;

/** Réplica única (memoria): no hay con quién competir, el pase siempre corre. */
public class LocalPairingLock implements PairingLock {

    @Override
    public boolean runExclusive(Runnable pass) {
        pass.run();
        return true;
    }
}
