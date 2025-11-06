package com.tartaritech.inventory_sync.services.exceptions;

public class DatabaseException extends RuntimeException  {

    public DatabaseException(String msg){
        super(msg);
    }
}
