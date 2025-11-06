package com.tartaritech.inventory_sync.services.exceptions;

public class ResourceNotFoundException extends RuntimeException  {

    public ResourceNotFoundException(String msg){
        super(msg);
    }
}
