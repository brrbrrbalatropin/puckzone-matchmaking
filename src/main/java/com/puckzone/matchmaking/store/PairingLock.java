package com.puckzone.matchmaking.store;

/**
 * Exclusión mutua del pase de emparejamiento: con varias réplicas, todas
 * tican cada segundo pero solo una debe recorrer la cola (dos pases
 * simultáneos podrían intentar emparejar a los mismos jugadores; el reclamo
 * atómico de la cola lo tolera, pero sería trabajo duplicado y logs dobles).
 * Si la réplica dueña muere, el lock expira y otra toma el relevo.
 */
public interface PairingLock {

    /**
     * Ejecuta el pase solo si esta réplica gana el lock.
     *
     * @return true si el pase corrió aquí
     */
    boolean runExclusive(Runnable pass);
}
