import { useEffect, useState } from 'react';
import type { MatchEventResponse } from '../types';
import { getMatchEvents } from '../api/matchesApi';

interface Props {
  matchId: number;
}

function eventLabel(e: MatchEventResponse): string {
  if (e.eventType === 'GOAL') {
    return e.assistPlayerName
      ? `${e.minute}' ${e.playerName} (assist: ${e.assistPlayerName})`
      : `${e.minute}' ${e.playerName}`;
  }
  return `${e.minute}' ${e.playerName}`;
}

export default function MatchEvents({ matchId }: Props) {
  const [events, setEvents] = useState<MatchEventResponse[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getMatchEvents(matchId)
      .then((res) => {
        if (!cancelled) setEvents(res.data);
      })
      .finally(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [matchId]);

  if (!loaded) return null;

  const goals = events.filter((e) => e.eventType === 'GOAL');
  const redCards = events.filter((e) => e.eventType === 'RED_CARD');

  if (goals.length === 0 && redCards.length === 0) return null;

  return (
    <div className="match-events" data-testid={`match-events-${matchId}`}>
      {goals.length > 0 && (
        <div className="match-events-group" data-testid={`match-events-goals-${matchId}`}>
          <h4>Goals</h4>
          <ul>
            {goals.map((e) => (
              <li key={e.id} data-testid={`match-event-${e.id}`}>{eventLabel(e)}</li>
            ))}
          </ul>
        </div>
      )}
      {redCards.length > 0 && (
        <div className="match-events-group" data-testid={`match-events-red-cards-${matchId}`}>
          <h4>Red Cards</h4>
          <ul>
            {redCards.map((e) => (
              <li key={e.id} data-testid={`match-event-${e.id}`}>{eventLabel(e)}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
