package com.example.worker_registry.Entitys;

public enum EstadoServicio {
    PENDIENTE,   // recién publicado
    ASIGNADO,    // oferta aceptada
    EN_PROGRESO,
    COMPLETADO,  // equivalente a "finalizado"; úsalo para HU007 más adelante
    CANCELADO
}
