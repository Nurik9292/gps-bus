package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.transport.infrastructure.debug.GpsRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_GPS_RECORDER;

@RestController
@RequestMapping(V1_ADMIN_GPS_RECORDER)
@CrossOrigin("*")
@ConditionalOnProperty(prefix = "gps.recorder", name = "enabled", havingValue = "true")
public class GpsRecorderController {

    private final GpsRecorder recorder;

    public GpsRecorderController(GpsRecorder recorder) {
        this.recorder = recorder;
    }

    @PostMapping("/start")
    public Mono<ResponseEntity<GpsRecorder.StartResult>> start(@RequestBody StartRequest request) {
        GpsRecorder.StartResult result = recorder.start(request.scenario(), request.durationSec());
        return Mono.just(result.started()
                ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(result));
    }

    @PostMapping("/stop")
    public Mono<ResponseEntity<GpsRecorder.StopResult>> stop() {
        GpsRecorder.StopResult result = recorder.stop();
        return Mono.just(result.stopped()
                ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(result));
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<GpsRecorder.RecorderStatus>> status() {
        return Mono.just(ResponseEntity.ok(recorder.status()));
    }

    public record StartRequest(String scenario, int durationSec) {}
}
