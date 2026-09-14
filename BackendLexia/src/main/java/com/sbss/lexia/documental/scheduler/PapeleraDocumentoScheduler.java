package com.sbss.lexia.documental.scheduler;

import com.sbss.lexia.documental.service.DocumentoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PapeleraDocumentoScheduler {

    private static final Logger log = LoggerFactory.getLogger(PapeleraDocumentoScheduler.class);
    private final DocumentoService documentoService;

    /** Cada madrugada elimina los documentos que cumplieron los 7 dias en papelera. */
    @Scheduled(cron = "${lexia.documentos.limpieza-cron:0 15 2 * * *}", zone = "America/Lima")
    public void limpiarPapelera() {
        int eliminados = documentoService.eliminarExpirados();
        if (eliminados > 0) {
            log.info("Papelera documental: {} documento(s) eliminado(s) definitivamente", eliminados);
        }
    }
}
