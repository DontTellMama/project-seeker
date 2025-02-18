package ru.homyakin.seeker.game.personage;

import io.vavr.control.Either;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.homyakin.seeker.game.event.service.EventService;
import ru.homyakin.seeker.game.event.service.LaunchedEventService;
import ru.homyakin.seeker.game.models.Money;
import ru.homyakin.seeker.game.personage.models.Personage;
import ru.homyakin.seeker.game.personage.models.errors.NameError;
import ru.homyakin.seeker.game.personage.models.errors.NotEnoughLevelingPoints;
import ru.homyakin.seeker.game.personage.models.errors.NotEnoughMoney;
import ru.homyakin.seeker.game.personage.models.errors.OrderError;
import ru.homyakin.seeker.game.tavern_menu.models.MenuItem;
import ru.homyakin.seeker.utils.models.Success;
import ru.homyakin.seeker.game.personage.models.errors.PersonageEventError;
import ru.homyakin.seeker.game.personage.models.errors.EventNotExist;
import ru.homyakin.seeker.game.personage.models.errors.ExpiredEvent;
import ru.homyakin.seeker.game.personage.models.errors.PersonageInOtherEvent;
import ru.homyakin.seeker.game.personage.models.errors.PersonageInThisEvent;

@Service
public class PersonageService {
    private static final Logger logger = LoggerFactory.getLogger(PersonageService.class);
    private final PersonageDao personageDao;
    private final LaunchedEventService launchedEventService;
    private final EventService eventService;

    public PersonageService(PersonageDao personageDao, LaunchedEventService launchedEventService, EventService eventService) {
        this.personageDao = personageDao;
        this.launchedEventService = launchedEventService;
        this.eventService = eventService;
    }

    public Personage createPersonage() {
        final var id = personageDao.save(Personage.createDefault());
        return personageDao.getById(id)
            .orElseThrow(() -> new IllegalStateException("Personage must be present after create"));
    }

    public Either<PersonageEventError, Success> addEvent(long personageId, Long launchedEventId) {
        final var requestedEvent = launchedEventService.getById(launchedEventId);
        if (requestedEvent.isEmpty()) {
            logger.error("Requested event " + launchedEventId + " doesn't present");
            return Either.left(new EventNotExist());
        } else if (!requestedEvent.get().isActive()) {
            return Either.left(eventService.getEventById(requestedEvent.get().eventId())
                .<PersonageEventError>map(ExpiredEvent::new)
                .orElseGet(EventNotExist::new)
            );
        }

        final var activeEvent = launchedEventService.getActiveEventByPersonageId(personageId);
        if (activeEvent.isEmpty()) {
            launchedEventService.addPersonageToLaunchedEvent(personageId, launchedEventId);
            return Either.right(new Success());
        }

        if (activeEvent.get().id() == launchedEventId) {
            return Either.left(new PersonageInThisEvent());
        } else {
            return Either.left(new PersonageInOtherEvent());
        }
    }

    public Optional<Personage> getById(long personageId) {
        return personageDao.getById(personageId)
            .map(personage -> personage.checkHealthAndRegenIfNeed(personageDao));
    }

    public Personage getByIdForce(long personageId) {
        return getById(personageId)
            .orElseThrow(() -> new IllegalStateException("Personage must be present with id " + personageId));
    }

    public List<Personage> getByLaunchedEvent(long launchedEventId) {
        return personageDao.getByLaunchedEvent(launchedEventId).stream()
            .map(personage -> personage.checkHealthAndRegenIfNeed(personageDao))
            .toList();
    }

    public Personage addMoney(Personage personage, Money money) {
        final var updatedPersonage = personage.addMoney(money);
        personageDao.update(updatedPersonage);
        return updatedPersonage;
    }

    public Personage takeMoney(Personage personage, Money money) {
        return addMoney(personage, money.negative());
    }

    public Either<OrderError, Personage> orderMenuItem(Personage personage, MenuItem menuItem) {
        if (!menuItem.isAvailable()) {
            return Either.left(new OrderError.NotAvailableItem());
        }
        if (personage.money().lessThan(menuItem.price())) {
            return Either.left(new OrderError.NotEnoughMoney(menuItem.price(), personage.money()));
        }
        return Either.right(takeMoney(personage, menuItem.price()));
    }

    public Either<NotEnoughLevelingPoints, Personage> incrementStrength(Personage personage) {
        return personage.incrementStrength().peek(personageDao::update);
    }

    public Either<NotEnoughLevelingPoints, Personage> incrementAgility(Personage personage) {
        return personage.incrementAgility().peek(personageDao::update);
    }

    public Either<NotEnoughLevelingPoints, Personage> incrementWisdom(Personage personage) {
        return personage.incrementWisdom().peek(personageDao::update);
    }

    public Either<NameError, Personage> changeName(Personage personage, String name) {
        return personage.changeName(name, personageDao);
    }

    public Either<NotEnoughMoney, Personage> resetStats(Personage personage) {
        return personage
            .resetStats()
            .peek(personageDao::update);
    }
}
