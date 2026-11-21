package kome.client;

import java.util.List;
import java.util.function.Consumer;
import kome.common.data.KOMEUnitMapMarker;

/** Full replacement stream, coalesced on the client thread and fenced to its connection/world. */
final class KOMEUnitMapMarkerPublisher {
    private final KOMEClientTaskQueue tasks;
    private final Consumer<List<KOMEUnitMapMarker>> publication;
    private Object connection;
    private long generation;
    KOMEUnitMapMarkerPublisher(KOMEClientTaskQueue tasks,Consumer<List<KOMEUnitMapMarker>> publication) {
        this.tasks=tasks;this.publication=publication;
    }
    synchronized void start(Object connection) {this.connection=connection;generation++;}
    synchronized void invalidateWorld() {generation++;}
    synchronized void accept(List<KOMEUnitMapMarker> markers,Object source) {
        if(connection==null||source!=connection)return;
        final long epoch=generation;
        tasks.enqueueLatestAtTail(this,()->{
            synchronized(KOMEUnitMapMarkerPublisher.this) {
                if(epoch==generation&&connection==source)publication.accept(markers);
            }
        });
    }
}
