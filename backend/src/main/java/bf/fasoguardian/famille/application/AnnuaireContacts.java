package bf.fasoguardian.famille.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.famille.ContactsUrgence;
import bf.fasoguardian.famille.infrastructure.DepotContacts;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AnnuaireContacts implements ContactsUrgence {

    private final DepotContacts contacts;
    private final ServiceChiffrement chiffrement;

    AnnuaireContacts(DepotContacts contacts, ServiceChiffrement chiffrement) {
        this.contacts = contacts;
        this.chiffrement = chiffrement;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contact> contactsDe(UUID enfantId) {
        return contacts.findByEnfantIdOrderByRang(enfantId).stream().map(contact -> new Contact(contact.id(), contact.lien(),
                chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, contact.telephoneChiffre()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Contact> contact(UUID enfantId, UUID contactId) {
        return contacts.findById(contactId).filter(contact -> contact.enfantId().equals(enfantId)).map(contact -> new Contact(
                contact.id(), contact.lien(), chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, contact.telephoneChiffre())));
    }
}
