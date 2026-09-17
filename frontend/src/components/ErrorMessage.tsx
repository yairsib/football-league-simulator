export default function ErrorMessage({ message }: { message: string }) {
  return (
    <div className="error-message">
      <strong>Error:</strong> {message}
    </div>
  );
}
