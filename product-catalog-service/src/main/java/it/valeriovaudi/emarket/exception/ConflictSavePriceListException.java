package it.valeriovaudi.emarket.exception;

import it.valeriovaudi.emarket.event.model.AbstractDomainEvent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Created by vvaudi on 11/05/17.
 */

@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictSavePriceListException extends AbstractException {

    public static final String DEFAULT_MESSAGE  = "error during the saving process: we have already saved the entity";

    public ConflictSavePriceListException(AbstractDomainEvent event, String msg) {
        super(event, msg);
    }

    public ConflictSavePriceListException(AbstractDomainEvent event, String msg, Throwable cause) {
        super(event, msg, cause);
    }
}

