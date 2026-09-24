package org.example.pongrankbackend.Community;

public enum CommunityStatus {
    ACTIVE,     //la comunidad aparece en la busqueda, acepta nuevos miembros, se puede editar
    //Comunidad eliminada temporalmente: solo lectura.
    //Oculta de las búsquedas, rechaza las solicitudes de ingreso y las actualizaciones,
    //pero los miembros existentes y las coincidencias anteriores permanecen intactos.
    ARCHIVED
}
