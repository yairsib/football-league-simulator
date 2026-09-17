export default function Loading({ message = 'Loading...' }: { message?: string }) {
  return (
    <div className="loading">
      <div className="spinner" />
      <p>{message}</p>
    </div>
  );
}
